package com.jeequan.jeepay.pay.channel.xxpay;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise the real SDK with each application's delivered logger policy. */
class SdkSigningLoggerConfigTest {
    @ParameterizedTest
    @ValueSource(strings = {"jeepay-manager", "jeepay-merchant", "jeepay-payment"})
    void sdkPreservesDigestWithoutLoggingKeysEvenUnderProjectTrace(String module) throws Exception {
        String sdkName = "com.jeequan.jeepay.util.JeepayKit";
        Logger parent = (Logger) LoggerFactory.getLogger("com.jeequan.jeepay");
        Logger sdk = (Logger) LoggerFactory.getLogger(sdkName);
        Level previousParent = parent.getLevel(), previousSdk = sdk.getLevel();
        boolean additive = sdk.isAdditive();
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start();
        sdk.addAppender(capture);
        sdk.setAdditive(false);
        try {
            parent.setLevel(Level.TRACE);
            sdk.setLevel(null);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Path config = Path.of("..").resolve(module).resolve("src/main/resources/logback-spring.xml");
            var entries = factory.newDocumentBuilder().parse(config.toFile()).getElementsByTagName("logger");
            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                if (sdkName.equals(entry.getAttribute("name"))) {
                    sdk.setLevel(Level.valueOf(entry.getAttribute("level")));
                }
            }
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("a", "中文&+");
            values.put("n", 0);
            values.put("flag", false);
            values.put("empty", "");
            values.put("nullValue", null);
            Map<String, Object> before = new LinkedHashMap<>(values);
            String expected = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(
                    "a=中文&+&flag=false&n=0&key=synthetic-sdk-key".getBytes(StandardCharsets.UTF_8)))
                    .toUpperCase(Locale.ROOT);
            assertEquals(expected, com.jeequan.jeepay.util.JeepayKit.getSign(values, "synthetic-sdk-key"));
            assertEquals(before, values);
            assertTrue(capture.list.isEmpty(), module + " must not log SDK signing material");
            sdk.warn("safe-sdk-warning");
            assertEquals("safe-sdk-warning", capture.list.get(0).getFormattedMessage());
            assertTrue(LoggerFactory.getLogger("com.jeequan.jeepay.unrelated").isDebugEnabled());
        } finally {
            sdk.detachAppender(capture);
            sdk.setLevel(previousSdk);
            sdk.setAdditive(additive);
            parent.setLevel(previousParent);
            capture.stop();
        }
    }
}
