def buildJob(branch, mode) {
    try {
        script {
            def job = build job: "storm-deployment-test-gpfs/" + branch, parameters: [string(name: 'MODE', value: mode)], wait: true, propagate: false
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
        cron('@midnight')
    }

    stages {
        stage("clean-master") {
            steps {
                buildJob("nightly", "clean")
            }
        }
        stage("update-master") {
            steps {
                buildJob("nightly", "update")
            }
        }
        stage("clean-stable") {
            steps {
                buildJob("stable", "clean")
            }
        }
        stage("update-stable") {
            steps {
                buildJob("stable", "update")
            }
        }
        stage("clean-beta") {
            steps {
                buildJob("beta", "clean")
            }
        }
        stage("update-beta") {
            steps {
                buildJob("beta", "update")
            }
        }
        stage("clean-umd4") {
            steps {
                buildJob("umd4", "clean")
            }
        }
    }
}
