suite = {

    # ==========================================================================
    #  METADATA
    # ==========================================================================
    "mxversion": "5.234.0",
    "name": "graalsqueak",
    "versionConflictResolution": "latest",

    "version": "19.2.0",
    "release": False,
    "groupId": "de.hpi.swa.graal.squeak",
    "url": "https://github.com/hpi-swa-lab/graalsqueak/",

    "developer": {
        "name": "Fabio Niephaus and contributors",
        "email": "code+graalsqueak@fniephaus.com",
        "organization": "Software Architecture Group, HPI, Potsdam, Germany",
        "organizationUrl": "https://www.hpi.uni-potsdam.de/swa/",
    },

    "scm": {
        "url": "https://github.com/hpi-swa-lab/graalsqueak/",
        "read": "https://github.com/hpi-swa-lab/graalsqueak.git",
        "write": "git@github.com:hpi-swa-lab/graalsqueak.git",
    },

    # "defaultLicense": "BSD-3-Clause",
    # "licenses": {
    #     "BSD-3-Clause": {
    #         "name": "The 3-Clause BSD License",
    #         "url": "http://opensource.org/licenses/BSD-3-Clause",
    #     },
    # },

    # ==========================================================================
    #  DEPENDENCIES
    # ==========================================================================
    "imports": {
        "suites": [{
            "name": "truffle",
            "subdir": True,
            "version": "bea1f627630bcb11c03e4df1e2d24e86ef32ee42",
            "urls": [{
                "url": "https://github.com/oracle/graal",
                "kind": "git"
            }],
        }],
    },

    # ==========================================================================
    #  LIBRARIES
    # ==========================================================================
    "libraries": {
        "BOUNCY_CASTLE_CRYPTO_LIB":  {
            "sha1": "bd47ad3bd14b8e82595c7adaa143501e60842a84",
            "maven": {
                "groupId": "org.bouncycastle",
                "artifactId": "bcprov-jdk15on",
                "version": "1.60"
            }
        },
    },

    # ==========================================================================
    #  PROJECTS
    # ==========================================================================
    "projects": {
        "de.hpi.swa.graal.squeak": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "graalsqueak:GRAALSQUEAK_SHARED",
                "BOUNCY_CASTLE_CRYPTO_LIB",
                "truffle:TRUFFLE_API",
            ],
            "checkstyleVersion": "8.8",
            "jacoco": "include",
            "javaCompliance": "8+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "GraalSqueak",
        },
        "de.hpi.swa.graal.squeak.launcher": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "graalsqueak:GRAALSQUEAK_SHARED",
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
                "truffle:TRUFFLE_API",
            ],
            "checkstyle": "de.hpi.swa.graal.squeak",
            "jacoco": "include",
            "javaCompliance": "8+",
            "workingSets": "GraalSqueak",
        },
        "de.hpi.swa.graal.squeak.shared": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "checkstyle": "de.hpi.swa.graal.squeak",
            "jacoco": "include",
            "javaCompliance": "8+",
            "workingSets": "GraalSqueak",
        },
        "de.hpi.swa.graal.squeak.tck": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "graalsqueak:GRAALSQUEAK_SHARED",
                "sdk:POLYGLOT_TCK",
                "mx:JUNIT"
            ],
            "checkstyle": "de.hpi.swa.graal.squeak",
            "javaCompliance": "8+",
            "workingSets": "GraalSqueak",
            "testProject": True,
        },
        "de.hpi.swa.graal.squeak.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "de.hpi.swa.graal.squeak",
                "mx:JUNIT"
            ],
            "checkstyle": "de.hpi.swa.graal.squeak",
            "jacoco": "include",
            "javaCompliance": "8+",
            "workingSets": "GraalSqueak",
            "testProject": True,
        },
    },

    # ==========================================================================
    #  DISTRIBUTIONS
    # ==========================================================================
    "distributions": {
        "GRAALSQUEAK": {
            "description": "GraalSqueak engine",
            "path": "graalsqueak.jar",
            "dependencies": [
                "de.hpi.swa.graal.squeak",
            ],
            "distDependencies": [
                "GRAALSQUEAK_SHARED",
                "truffle:TRUFFLE_API",
            ],
            "exclude": ["mx:JUNIT"],
            "sourcesPath": "graalsqueak.src.zip",
        },

        "GRAALSQUEAK_SHARED": {
            "dependencies": [
                "de.hpi.swa.graal.squeak.shared",
            ],
            "path": "graalsqueak-shared.jar",
            "sourcesPath": "graalsqueak-shared.src.zip",
        },

        "GRAALSQUEAK_LAUNCHER": {
            "path": "graalsqueak-launcher.jar",
            "dependencies": [
                "de.hpi.swa.graal.squeak.launcher",
            ],
            "distDependencies": [
                "GRAALSQUEAK_SHARED",
                "sdk:GRAAL_SDK",
                "truffle:TRUFFLE_API",
                "sdk:LAUNCHER_COMMON",
            ],
            "sourcesPath": "graalsqueak-launcher.src.zip",
        },

        "GRAALSQUEAK_TCK": {
            "description": "TCK-based interoperability tests",
            "dependencies": [
                "de.hpi.swa.graal.squeak.tck",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "GRAALSQUEAK_SHARED",
                "sdk:POLYGLOT_TCK",
            ],
            "sourcesPath": "graalsqueak.tck.src.zip",
            "testDistribution": True,
        },

        "GRAALSQUEAK_GRAALVM_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "description": "GraalSqueak support distribution for the GraalVM",
            "layout": {
                "./": [
                    "file:mx.graalsqueak/native-image.properties",
                ],
            },
            "maven": False,
        },

        "GRAALSQUEAK_TEST": {
            "description": "unit tests",
            "path": "graalsqueak_test.jar",
            "javaCompliance": "8+",
            "dependencies": [
                "de.hpi.swa.graal.squeak.test",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [],
            "sourcesPath": "graalsqueak.tests.src.zip",
            "testDistribution": True,
        },
    },
}
