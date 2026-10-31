package dev.srimi.depthwall.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal

/** Unity 2019.3's allocator crashes on 64-bit Android 11+; an APK without 64-bit libraries runs as 32-bit. Pure, so it runs on the JVM. */
object Pack32 {
    private val dropped=listOf("lib/arm64-v8a/","lib/x86_64/","lib/x86/")
    private val signatureFiles=listOf(".SF",".RSA",".DSA",".EC")
    fun isSignatureFile(name:String):Boolean {
        if(!name.startsWith("META-INF/") || name.indexOf('/',9)>=0) return false
        val file=name.substring(9).uppercase()
        return file=="MANIFEST.MF" || file.startsWith("SIG-") || signatureFiles.any { file.endsWith(it) }
    }
    /** Entries copied into the 32-bit APK, in their original order. */
    fun keep(names:List<String>):List<String> {
        check(names.any { it.startsWith("lib/armeabi-v7a/") && it.endsWith(".so") }) { "This pack has no 32-bit engine" }
        return names.filter { name -> dropped.none { name.startsWith(it) } && !isSignatureFile(name) && name!="stamp-cert-sha256" }
    }
}

object Pack32Builder {
    private const val ALIAS="depthwall_pack32"
    fun requireSupported()=check("armeabi-v7a" in Build.SUPPORTED_32_BIT_ABIS) { "This phone can't run 32-bit apps" }
    fun build(source:File,output:File) {
        requireSupported()
        val dir=output.parentFile!!
        require(dir.usableSpace>2*source.length()+8L*1024*1024) { "Not enough free storage." }
        val unsigned=File(dir,output.name.removeSuffix(".apk")+".tmp")
        try {
            output.delete()
            ZipFile(source).use { zip ->
                val keep=Pack32.keep(zip.entries().asSequence().map { it.name }.toList()).toHashSet()
                ZipOutputStream(unsigned.outputStream().buffered()).use { out ->
                    zip.entries().asSequence().filter { it.name in keep }.forEach { entry ->
                        out.putNextEntry(ZipEntry(entry.name).apply {
                            method=entry.method;time=entry.time
                            if(entry.method==ZipEntry.STORED) { size=entry.size;compressedSize=entry.size;crc=entry.crc }
                        })
                        if(!entry.isDirectory) zip.getInputStream(entry).use { it.copyTo(out,16384) }
                        out.closeEntry()
                    }
                }
            }
            val (key,certificate)=signingKey()
            ApkSigner.Builder(listOf(ApkSigner.SignerConfig.Builder("DEPTHWALL",key,listOf(certificate)).build()))
                .setInputApk(unsigned).setOutputApk(output)
                .setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true).setV4SigningEnabled(false)
                .setMinSdkVersion(26)
                .build().sign()
            val result=ApkVerifier.Builder(output).setMinCheckedPlatformVersion(26).build().verify()
            check(result.isVerified) { "The 32-bit pack failed signature verification: ${result.errors.firstOrNull() ?: "unknown error"}" }
        } catch(e:Exception) { output.delete();throw e }
        finally { unsigned.delete() }
    }
    private fun signingKey():Pair<PrivateKey,X509Certificate> {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if(!store.containsAlias(ALIAS)) {
            val end=Calendar.getInstance().apply { add(Calendar.YEAR,30) }.time
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(3072)
                    .setCertificateSubject(X500Principal("CN=DepthWall 32-bit pack"))
                    .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                    .setCertificateNotBefore(Date(System.currentTimeMillis()-24L*60*60*1000))
                    .setCertificateNotAfter(end)
                    .build())
            }.generateKeyPair()
        }
        val key=store.getKey(ALIAS,null) as? PrivateKey ?: error("The 32-bit signing key is unavailable.")
        val certificate=store.getCertificate(ALIAS) as? X509Certificate ?: error("The 32-bit signing certificate is unavailable.")
        return key to certificate
    }
}
