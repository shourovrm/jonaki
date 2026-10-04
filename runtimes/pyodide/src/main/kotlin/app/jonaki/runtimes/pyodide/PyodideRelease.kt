package app.jonaki.runtimes.pyodide

/**
 * One Pyodide release: where it is downloaded from and the SHA-256 of every
 * core file. Packages are checked against the sha256 in the release's
 * pyodide-lock.json, which is itself a pinned core file, so every byte the
 * app runs traces back to the hashes in this file.
 */
data class PyodideRelease(
    val version: String,
    /** Ends with "/"; core files and packages are both under it. */
    val baseUrl: String,
    val coreFiles: List<PinnedFile>,
    /** Pure-Python wheels outside the lock file, pinned by URL and hash (the documents add-on). */
    val addOnWheels: List<PinnedWheel> = emptyList(),
) {
    val coreDownloadBytes: Long = coreFiles.sumOf { file -> file.sizeBytes }

    companion object {
        const val LOCK_FILE_NAME = "pyodide-lock.json"

        /** numpy and pandas; their dependencies come from the lock file. */
        val DATA_ADD_ON: List<String> = listOf("numpy", "pandas")

        /**
         * The data add-on with its dependencies (python-dateutil, pytz, six),
         * measured in spike S-2 (D-013). The lock file gives no sizes, so other
         * packages have no known size before their download.
         */
        const val DATA_ADD_ON_DOWNLOAD_BYTES: Long = 7_889_748

        /**
         * The documents add-on's packages from the lock file: lxml and pillow
         * (WebAssembly builds) and beautifulsoup4 with its dependencies
         * soupsieve and typing-extensions.
         */
        val DOCUMENTS_ADD_ON_LOCK_PACKAGES: List<String> =
            listOf("lxml", "pillow", "typing-extensions", "beautifulsoup4", "soupsieve")

        /**
         * Measured on 2026-10-05 by downloading the five files from the CDN
         * (each matched the lock file's sha256): lxml 2,145,850, pillow
         * 1,037,806, typing_extensions 44,613, beautifulsoup4 107,721 and
         * soupsieve 37,015 bytes.
         */
        const val DOCUMENTS_ADD_ON_LOCK_DOWNLOAD_BYTES: Long = 3_373_005

        /**
         * The five wheels, exactly as PyPI lists them (url, digests.sha256
         * and size from https://pypi.org/pypi/<name>/<version>/json on
         * 2026-10-05). All are MIT or BSD licensed. They are not in the lock
         * file and Pyodide loads them from a path without resolving
         * dependencies, so every byte is pinned here like a core file.
         */
        val DOCUMENTS_ADD_ON_WHEELS: List<PinnedWheel> = listOf(
            PinnedWheel(
                packageName = "python-docx",
                version = "1.2.0",
                importNames = listOf("docx"),
                fileName = "python_docx-1.2.0-py3-none-any.whl",
                url = "https://files.pythonhosted.org/packages/d0/00/1e03a4989fa5795da308cd774f05b704ace555a70f9bf9d3be057b680bcf/python_docx-1.2.0-py3-none-any.whl",
                sha256 = "3fd478f3250fbbbfd3b94fe1e985955737c145627498896a8a6bf81f4baf66c7",
                sizeBytes = 252_987,
            ),
            PinnedWheel(
                packageName = "openpyxl",
                version = "3.1.5",
                importNames = listOf("openpyxl"),
                fileName = "openpyxl-3.1.5-py2.py3-none-any.whl",
                url = "https://files.pythonhosted.org/packages/c0/da/977ded879c29cbd04de313843e76868e6e13408a94ed6b987245dc7c8506/openpyxl-3.1.5-py2.py3-none-any.whl",
                sha256 = "5282c12b107bffeef825f4617dc029afaf41d0ea60823bbb665ef3079dc79de2",
                sizeBytes = 250_910,
            ),
            PinnedWheel(
                packageName = "et-xmlfile",
                version = "2.0.0",
                importNames = listOf("et_xmlfile"),
                fileName = "et_xmlfile-2.0.0-py3-none-any.whl",
                url = "https://files.pythonhosted.org/packages/c1/8b/5fe2cc11fee489817272089c4203e679c63b570a5aaeb18d852ae3cbba6a/et_xmlfile-2.0.0-py3-none-any.whl",
                sha256 = "7a91720bc756843502c3b7504c77b8fe44217c85c537d85037f0f536151b2caa",
                sizeBytes = 18_059,
            ),
            PinnedWheel(
                packageName = "python-pptx",
                version = "1.0.2",
                importNames = listOf("pptx"),
                fileName = "python_pptx-1.0.2-py3-none-any.whl",
                url = "https://files.pythonhosted.org/packages/d9/4f/00be2196329ebbff56ce564aa94efb0fbc828d00de250b1980de1a34ab49/python_pptx-1.0.2-py3-none-any.whl",
                sha256 = "160838e0b8565a8b1f67947675886e9fea18aa5e795db7ae531606d68e785cba",
                sizeBytes = 472_788,
            ),
            PinnedWheel(
                packageName = "XlsxWriter",
                version = "3.2.9",
                importNames = listOf("xlsxwriter"),
                fileName = "xlsxwriter-3.2.9-py3-none-any.whl",
                url = "https://files.pythonhosted.org/packages/3a/0c/3662f4a66880196a590b202f0db82d919dd2f89e99a27fadef91c4a33d41/xlsxwriter-3.2.9-py3-none-any.whl",
                sha256 = "9a5db42bc5dff014806c58a20b9eae7322a134abb6fce3c92c181bfb275ec5b3",
                sizeBytes = 175_315,
            ),
        )

        /** Lock packages and wheels together: 3,373,005 + 1,170,059 bytes. */
        val DOCUMENTS_ADD_ON_DOWNLOAD_BYTES: Long =
            DOCUMENTS_ADD_ON_LOCK_DOWNLOAD_BYTES + DOCUMENTS_ADD_ON_WHEELS.sumOf { wheel -> wheel.sizeBytes }

        /** Every package name of the documents add-on, lock packages first; the install and its progress are keyed by these. */
        val DOCUMENTS_ADD_ON: List<String> =
            DOCUMENTS_ADD_ON_LOCK_PACKAGES + DOCUMENTS_ADD_ON_WHEELS.map { wheel -> wheel.packageName }

        /**
         * Importing any of these means the program needs the whole documents
         * add-on: the wheels' modules and the bundled helper that builds on them.
         */
        val DOCUMENTS_ADD_ON_IMPORTS: Set<String> =
            DOCUMENTS_ADD_ON_WHEELS.flatMap { wheel -> wheel.importNames }.toSet() + BundledModules.NAMES

        /**
         * Pyodide 314.0.7 (Python 3.14.2), the latest stable release on
         * 2026-10-03. The hashes match the CDN files, the npm package
         * pyodide@314.0.7 (its sha512 integrity checked against the npm
         * registry) and the GitHub release archive pyodide-core-314.0.7.tar.bz2
         * (D-069).
         */
        val PINNED: PyodideRelease = PyodideRelease(
            version = "314.0.7",
            baseUrl = "https://cdn.jsdelivr.net/pyodide/v314.0.7/full/",
            coreFiles = listOf(
                PinnedFile(
                    name = "pyodide.js",
                    sha256 = "3141b814715a72e59b51b1b18b9ceae5bf19f7c852417e431bb0a34feadf825c",
                    sizeBytes = 18_912,
                ),
                PinnedFile(
                    name = "pyodide.asm.mjs",
                    sha256 = "f7cdc8ece80678ceb712f8e65ebe6d3a83203a180c399865f49612a051693635",
                    sizeBytes = 1_250_344,
                ),
                PinnedFile(
                    name = "pyodide.asm.wasm",
                    sha256 = "cc36e3cab04fdfc9a63ff13eb52eae2b911bf46c025cc7b281f394bd3de1d5e6",
                    sizeBytes = 9_598_218,
                ),
                PinnedFile(
                    name = "python_stdlib.zip",
                    sha256 = "fa1957e5777068fc4f7437f96d860ae2fbe9c19732ba06c84e004ec16dd7dd7a",
                    sizeBytes = 2_545_637,
                ),
                PinnedFile(
                    name = LOCK_FILE_NAME,
                    sha256 = "5dc2fc119108bc148c7457dc86e7675b5c87e1cafd420b9c34c1eaef7b36c010",
                    sizeBytes = 119_077,
                ),
            ),
            addOnWheels = DOCUMENTS_ADD_ON_WHEELS,
        )
    }
}

data class PinnedFile(
    val name: String,
    /** Lowercase hexadecimal. */
    val sha256: String,
    val sizeBytes: Long,
)

/** A pure-Python wheel that is not in the lock file, downloaded from [url] and kept only on a SHA-256 match. */
data class PinnedWheel(
    /** The distribution name, as PyPI writes it, for example "python-docx". */
    val packageName: String,
    val version: String,
    /** What a program imports to use it, for example "docx". */
    val importNames: List<String>,
    /** A plain file name; it is where the wheel lives in the Pyodide folder and under pyodide/ for the worker. */
    val fileName: String,
    val url: String,
    /** Lowercase hexadecimal. */
    val sha256: String,
    val sizeBytes: Long,
)
