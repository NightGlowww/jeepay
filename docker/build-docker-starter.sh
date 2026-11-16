#!/bin/sh
set -o errexit

# 版本
VERSION=latest
# DockerHub 用户名前缀 / 私有镜像仓库，需要提前登陆好，编译完成后会自动上传
DOCKER_NAMESPACE=registry.cn-beijing.aliyuncs.com/jiquankeji
# 容器名字前缀
IMAGE_NAME=jeepay
# buildx toolkit 名称
BUILDER=${IMAGE_NAME}-builder
# 平台
PLATFORM=linux/amd64,linux/arm64
FRONTEND_DEPS_IMAGE_NAME=${DOCKER_NAMESPACE}/${IMAGE_NAME}-ui-deps:$VERSION

UI_FOLDER=./private-jeepay-ui

TARGET=$1
HAS_DEPS=$2
ECHO_NAME=$3

docker_buildx() {
  build_path=$1
  build_name=$2
  build_file=$3
  build_arg=""

  for i in "$@"; do
    if [ "$i" = "$build_path" ] || [ "$i" = "$build_name" ] || [ "$i" = "$build_file" ]; then
      continue
    fi
    build_arg="$build_arg --build-arg $i"
  done

  if [ "$ECHO_NAME" = "name" ]; then
    echo "${IMAGE_NAME}-${build_name}"
  else
    CMD="docker buildx build ${build_path} -f ${build_file} --platform ${PLATFORM} ${build_arg} -t ${DOCKER_NAMESPACE}/${IMAGE_NAME}-${build_name}:$VERSION --push"
    echo "$CMD"
    eval "$CMD"
  fi
}

build_backend() {

  echo "== 项目后端"
  # 当前每个模块的 Dockerfile 直接复制已打包 JAR，不再使用 docs/Dockerfile 依赖镜像。
  # 保留旧的 deps 参数以兼容调用方，但不再构建已删除的依赖层。
  for service in payment manager merchant; do
    if [ "$ECHO_NAME" != "name" ] && [ ! -f "jeepay-$service/target/jeepay-$service.jar" ]; then
      echo "ERROR: 缺少 jeepay-$service/target/jeepay-$service.jar，请先在仓库根目录执行 mvn clean package -DskipTests" >&2
      exit 1
    fi
    docker_buildx "./jeepay-$service" "$service" "./jeepay-$service/Dockerfile"
  done

}

build_frontend() {

  rm -rf ${UI_FOLDER}
  git clone https://gitee.com/jeequan/jeepay-ui.git ${UI_FOLDER}

  echo "== 项目前端"

  if [ "$HAS_DEPS" = "deps" ]; then
    docker_buildx ${UI_FOLDER} ui-deps "${UI_FOLDER}/Dockerfile-deps"
  fi

  docker_buildx ${UI_FOLDER} ui-payment "${UI_FOLDER}/Dockerfile" PLATFORM=cashier IMAGES="$FRONTEND_DEPS_IMAGE_NAME"

  docker_buildx ${UI_FOLDER} ui-manager "${UI_FOLDER}/Dockerfile" PLATFORM=manager IMAGES="$FRONTEND_DEPS_IMAGE_NAME"

  docker_buildx ${UI_FOLDER} ui-merchant "${UI_FOLDER}/Dockerfile" PLATFORM=merchant IMAGES="$FRONTEND_DEPS_IMAGE_NAME"

}

build_deps() {
  echo "== 项目依赖环境编译"

  docker_buildx ./docker/activemq activemq ./docker/activemq/Dockerfile
}

if [ "$TARGET" != "" ] && [ "$ECHO_NAME" != "name" ]; then
  docker buildx rm ${BUILDER} || true

  docker buildx create --use --name ${BUILDER} --platform linux/arm64,linux/amd64
fi

if [ "$TARGET" = "backend" ] || [ "$TARGET" = "all" ]; then
  build_backend
fi

if [ "$TARGET" = "frontend" ] || [ "$TARGET" = "all" ]; then
  build_frontend
fi

if [ "$TARGET" = "all" ]; then
  build_deps
elif [ "$TARGET" != "backend" ] && [ "$TARGET" != "frontend" ]; then
  echo "./build-docker.sh [backend|frontend|all] [deps] [name]"
fi
