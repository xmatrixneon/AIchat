package com.settingpro.camera.security

import android.util.Log
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.cert.CertificateException
import java.security.cert.PKIXParameters
import java.security.cert.CertPathValidator
import java.security.cert.TrustAnchor
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * SSL Certificate Pinning for secure WebSocket connections
 * Prevents Man-in-the-Middle attacks
 */
object SslPinningManager {

    private const val TAG = "SslPinningManager"

    // Load native library for advanced pinning checks
    init {
        try {
            System.loadLibrary("aicrypt")
            Log.d(TAG, "Native SSL pinning library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library not available, using Java implementation")
        }
    }

    /**
     * Native SSL pinning check
     * Returns true if certificate is valid, false otherwise
     */
    external fun nativeVerifyCertificate(certData: ByteArray, hostname: String): Boolean

    /**
     * Detect SSL pinning bypass tools (Frida, SSL Kill Switch, etc.)
     */
    external fun detectSslBypass(): Boolean

    /**
     * Create SSLContext with certificate pinning
     */
    fun createPinnedSSLContext(pinnedCerts: Array<String>): SSLContext {
        try {
            val trustManager = PinnedTrustManager(pinnedCerts)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), null)
            return sslContext
        } catch (e: Exception) {
            Log.e(TAG, "Error creating pinned SSL context", e)
            throw e
        }
    }

    /**
     * Create SSLSocketFactory with pinning
     */
    fun createPinnedSSLSocketFactory(pinnedCerts: Array<String>): SSLSocketFactory {
        val sslContext = createPinnedSSLContext(pinnedCerts)
        return sslContext.socketFactory
    }

    /**
     * Create HostnameVerifier with pinning
     */
    fun createPinnedHostnameVerifier(pinnedCerts: Array<String>): HostnameVerifier {
        return PinnedHostnameVerifier(pinnedCerts)
    }

    /**
     * Pin certificate for specific host
     */
    fun pinCertificate(hostname: String, certData: ByteArray): Boolean {
        // Check certificate using native implementation
        return nativeVerifyCertificate(certData, hostname)
    }

    /**
     * Validate certificate chain
     */
    fun validateCertificateChain(chain: Array<X509Certificate>): Boolean {
        try {
            val certFactory = CertificateFactory.getInstance("X.509")
            val params = PKIXParameters(HashSet<TrustAnchor>())
            params.isRevocationEnabled = false

            val validator = CertPathValidator.getInstance("PKIX")
            val certPath = certFactory.generateCertPath(chain.toList())

            validator.validate(certPath, params)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Certificate chain validation failed", e)
            return false
        }
    }

    /**
     * Check for SSL pinning bypass indicators
     */
    fun isSslBypassDetected(): Boolean {
        // Check using native implementation
        val nativeDetected = detectSslBypass()
        if (nativeDetected) return true

        // Additional Java checks
        val suspiciousLibs = arrayOf(
            "libfrida",
            "libxposed",
            "libsubstrate",
            "libssl_kill_switch"
        )

        for (lib in suspiciousLibs) {
            if (isLibraryLoaded(lib)) {
                Log.w(TAG, "Suspicious library detected: $lib")
                return true
            }
        }

        return false
    }

    /**
     * Check if a native library is loaded
     */
    private fun isLibraryLoaded(libName: String): Boolean {
        return try {
            System.loadLibrary(libName)
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    /**
     * Custom TrustManager for certificate pinning
     */
    private class PinnedTrustManager(private val pinnedCerts: Array<String>) : X509TrustManager {

        private val pinnedCertificates by lazy {
            loadPinnedCertificates()
        }

        private fun loadPinnedCertificates(): List<X509Certificate> {
            val certs = mutableListOf<X509Certificate>()
            val certFactory = CertificateFactory.getInstance("X.509")

            for (cert in pinnedCerts) {
                try {
                    val certBytes = android.util.Base64.decode(cert, android.util.Base64.DEFAULT)
                    val inputStream = ByteArrayInputStream(certBytes)
                    val x509Cert = certFactory.generateCertificate(inputStream) as X509Certificate
                    certs.add(x509Cert)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading pinned certificate", e)
                }
            }

            return certs
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            // Client certificates not used
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain == null || chain.isEmpty()) {
                throw CertificateException("Certificate chain is empty")
            }

            // Check against pinned certificates
            val leafCert = chain[0]
            for (pinnedCert in pinnedCertificates) {
                if (leafCert == pinnedCert) {
                    Log.d(TAG, "Certificate pinning verified")
                    return
                }
            }

            // If not pinned, validate chain normally
            if (!validateCertificateChain(chain as Array<X509Certificate>)) {
                throw CertificateException("Certificate chain validation failed")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return emptyArray()
        }
    }

    /**
     * Custom HostnameVerifier for certificate pinning
     */
    private class PinnedHostnameVerifier(private val pinnedCerts: Array<String>) : HostnameVerifier {

        override fun verify(hostname: String?, session: javax.net.ssl.SSLSession?): Boolean {
            if (hostname == null) return false

            try {
                val cert = session?.getPeerCertificates()?.firstOrNull() as? X509Certificate
                if (cert == null) {
                    Log.w(TAG, "No certificate found in session")
                    return false
                }

                // Verify hostname matches certificate
                val hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
                if (!hostnameVerifier.verify(hostname, session)) {
                    Log.w(TAG, "Hostname verification failed for: $hostname")
                    return false
                }

                // Verify certificate pin
                val certData = cert.encoded
                return pinCertificate(hostname, certData)

            } catch (e: Exception) {
                Log.e(TAG, "Hostname verification error", e)
                return false
            }
        }
    }

    /**
     * Create a certificate from a PEM string
     */
    fun createCertificateFromPEM(pemCert: String): X509Certificate {
        val certFactory = CertificateFactory.getInstance("X.509")
        val decoded = android.util.Base64.decode(pemCert, android.util.Base64.DEFAULT)
        val inputStream = ByteArrayInputStream(decoded)
        return certFactory.generateCertificate(inputStream) as X509Certificate
    }

    /**
     * Extract certificate from HTTPS connection
     */
    fun extractCertificateFromConnection(url: String): X509Certificate? {
        return try {
            val connection = java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
            connection.connect()

            val certs = connection.serverCertificates
            if (certs.isNotEmpty()) {
                certs.firstOrNull { it is X509Certificate } as? X509Certificate
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting certificate", e)
            null
        }
    }

    /**
     * Pin certificate for specific domain (stored securely)
     */
    data class PinnedCertificate(
        val domain: String,
        val certificateHash: String,
        val publicKey: String,
        val validUntil: Long
    )

    /**
     * Get pinned certificates for your domains
     * Store these securely (encrypted in SharedPreferences)
     */
    fun getPinnedCertificates(): List<PinnedCertificate> {
        // Load from secure storage
        return listOf(
            // Add your certificates here
            // PinnedCertificate(
            //     domain = "your-server.com",
            //     certificateHash = "sha256/XXXXXXXXXXXX",
            //     publicKey = "XXXXXXXX",
            //     validUntil = 1234567890L
            // )
        )
    }
}
