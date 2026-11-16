#!/bin/sh
# 离线回归：只运行临时目录中的 docker mock，不创建 builder、构建或推送镜像。
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
BUILD_TEST_SCRIPT=${BUILD_TEST_SCRIPT:-$ROOT_DIR/docker/build-docker-starter.sh}
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT HUP INT TERM
mkdir -p "$TEST_DIR/bin" "$TEST_DIR/repo/docker"
cp "$BUILD_TEST_SCRIPT" "$TEST_DIR/repo/docker/build-docker-starter.sh"
for service in payment manager merchant; do
    mkdir -p "$TEST_DIR/repo/jeepay-$service/target"
    cp "$ROOT_DIR/jeepay-$service/Dockerfile" "$TEST_DIR/repo/jeepay-$service/"
    touch "$TEST_DIR/repo/jeepay-$service/target/jeepay-$service.jar"
done
cat > "$TEST_DIR/bin/docker" <<'MOCK'
#!/bin/sh
printf '%s\n' "$*" >> "$BUILD_TEST_LOG"
if [ "$1 $2" = "buildx build" ]; then
    test -d "$3"
    test "$4" = "-f"
    test -f "$5"
fi
MOCK
chmod +x "$TEST_DIR/bin/docker"
export PATH="$TEST_DIR/bin:$PATH"
export BUILD_TEST_LOG="$TEST_DIR/docker.log"
(
    cd "$TEST_DIR/repo"
    sh docker/build-docker-starter.sh backend deps > /dev/null
)
test "$(grep -c '^buildx build ' "$BUILD_TEST_LOG")" -eq 3
rm "$BUILD_TEST_LOG"
names=$(cd "$TEST_DIR/repo" && sh docker/build-docker-starter.sh backend deps name)
test ! -e "$BUILD_TEST_LOG"
for service in payment manager merchant; do
    printf '%s\n' "$names" | grep -qx "jeepay-$service"
done
echo PASS
