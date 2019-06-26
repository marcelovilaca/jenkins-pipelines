def buildJob(upgradeFrom, targetRelease) {
    try {
        script {
            def job = build job: "storm-deployment-tests/master", 
                parameters: [
                    string(name: 'UPGRADE_FROM', value: upgradeFrom),
                    string(name: 'TARGET_RELEASE', value: targetRelease)
                ], 
                wait: true, 
                propagate: false
            if (job.result.equals("SUCCESS")) {
            } else {
                error 'FAIL'
            }
        }
    } catch (e) {
        currentBuild.result = 'UNSTABLE'
    }
}

pipeline {
    agent none

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
    }

    triggers {
        cron('@daily')
    }

    stages {
        stage("clean-deployment-nightly") {
            steps {
                buildJob("", "nightly")
            }
        }
        stage("clean-deployment-beta") {
            steps {
                buildJob("", "beta")
            }
        }
        stage("clean-deployment-stable") {
            steps {
                buildJob("", "stable")
            }
        }
        stage("upgrade-from-stable-to-nightly") {
            steps {
                buildJob("stable", "nightly")
            }
        }
        stage("upgrade-from-stable-to-beta") {
            steps {
                buildJob("stable", "beta")
            }
        }
        stage("upgrade-from-umd-to-nightly") {
            steps {
                buildJob("umd", "nightly")
            }
        }
        stage("upgrade-from-umd-to-stable") {
            steps {
                buildJob("umd", "stable")
            }
        }
    }
}