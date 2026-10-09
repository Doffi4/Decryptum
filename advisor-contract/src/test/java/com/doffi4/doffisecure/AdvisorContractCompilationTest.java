package com.doffi4.doffisecure;

import com.doffi4.doffisecure.domain.advisor.AdvisorPayloadJson;
import com.doffi4.doffisecure.domain.advisor.AdvisorSummary;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Compiles against the real public Kotlin/JVM API, not a simulated whitelist. */
public class AdvisorContractCompilationTest {
    @Rule public TemporaryFolder output = new TemporaryFolder();

    @Test public void onlyCountDtoCanBePassedToWriter() throws Exception {
        // Positive control proves compiler/classpath failures cannot masquerade as privacy protection.
        assertTrue(compiles("AdvisorPayloadJson.INSTANCE.encode(new AdvisorSummary(5, 2, 3, 0));"));
        for (String forbidden : Arrays.asList(
            "\"PASSWORD_USERNAME_EMAIL_DOMAIN_URL_PACKAGE_ID_KEY_CLIPBOARD_EXCEPTION\"",
            "new byte[]{1,2,3}", // hashes, HIBP prefixes, TOTP/passkey/key bytes
            "new RuntimeException(\"PRIVATE_EXCEPTION\")",
            "new Object()", // vault/domain/database objects
            "java.util.Collections.singletonMap(\"password\", \"PRIVATE_SECRET\")",
            "java.util.Collections.singletonList(\"PRIVATE_ACCOUNT\")"
        )) {
            assertFalse("Writer accepted prohibited input: " + forbidden,
                compiles("AdvisorPayloadJson.INSTANCE.encode(" + forbidden + ");"));
        }
    }

    @Test public void dtoCannotAcceptAdditionalFieldsOrSecretValues() throws Exception {
        assertTrue(compiles("new AdvisorSummary(5, 2, 3, 0);"));
        assertFalse(compiles("new AdvisorSummary(5, 2, 3, 0, \"PRIVATE_PASSWORD\");"));
        assertFalse(compiles("new AdvisorSummary(\"PRIVATE_HASH\", 2, 3, 0);"));
        assertFalse(compiles("new AdvisorSummary(5, 2, 3, 0) { public String password = \"PRIVATE_SECRET\"; };"));
        for (String getter : Arrays.asList(
            "getPassword", "getPasswordHash", "getHibpPrefix", "getTotpSecret",
            "getPasskeyPrivateKey", "getPasskeyPublicKey", "getUsername", "getEmail",
            "getService", "getDomain", "getUrl", "getPackageName", "getDatabaseId",
            "getMasterPassword", "getEncryptionKey", "getException", "getClipboard"
        )) {
            assertFalse("Contract exposes prohibited field: " + getter,
                compiles("new AdvisorSummary(5, 2, 3, 0)." + getter + "();"));
        }
    }

    private boolean compiles(String statement) throws Exception {
        // Android's unit-test compile bootclasspath has no javax.tools. Invoke the test JDK's
        // compiler directly instead; this test is never packaged into either Android variant.
        File javaBin = new File(System.getProperty("java.home"), "bin");
        File compiler = new File(javaBin, System.getProperty("os.name").startsWith("Windows") ? "javac.exe" : "javac");
        assertTrue("Run contract tests with a JDK, not a JRE", compiler.isFile());
        String source = "import com.doffi4.doffisecure.domain.advisor.*; "
            + "class CompileProbe { void probe() { " + statement + " } }";
        File file = new File(output.getRoot(), "CompileProbe.java");
        Files.write(file.toPath(), source.getBytes(StandardCharsets.UTF_8));
        String classpath = new File(AdvisorSummary.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath()
            + File.pathSeparator + new File(AdvisorPayloadJson.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath()
            + File.pathSeparator + new File(kotlin.Unit.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        File diagnostics = new File(output.getRoot(), "javac-diagnostics.txt");
        Process process = new ProcessBuilder(compiler.getPath(), "-proc:none", "-classpath", classpath,
            "-d", output.getRoot().getPath(), file.getPath()).redirectErrorStream(true).redirectOutput(diagnostics).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Contract compilation timed out");
        }
        return process.exitValue() == 0;
    }
}
