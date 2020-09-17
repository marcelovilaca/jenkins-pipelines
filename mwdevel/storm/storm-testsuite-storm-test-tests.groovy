#!/usr/bin/env groovy

@Library('sd')_
def kubeLabel = getKubeLabel()
def runner_job

pipeline {
  agent {
    kubernetes {
      label "${kubeLabel}"
      cloud 'Kube mwdevel'
      defaultContainer 'runner'
      inheritFrom 'ci-template'
    }
  }

  options {
    timeout(time: 3, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers {
    cron('@daily')
  }

  parameters {
    string(name: 'TESTSUITE_BRANCH', defaultValue: 'test_one', description: 'Which branch of storm-testsuite_runner' )
    string(name: 'TESTSUITE_EXCLUDE', defaultValue: "to-be-fixedORcdmi", description: '')
    string(name: 'TESTSUITE_SUITE', defaultValue: "tests", description: '')
  }

  environment {
    JOB_NAME = 'storm-testsuite_runner'
  }

  stages {
    stage('run-testsuite') {
      steps {
        script {
          catchError{
            runner_job = build job: "${env.JOB_NAME}/${params.TESTSUITE_BRANCH}", propagate: false, parameters: [
              string(name: 'STORM_BACKEND_HOSTNAME', value: "storm-test.cr.cnaf.infn.it"),
              string(name: 'STORM_FRONTEND_HOSTNAME', value: "storm-test.cr.cnaf.infn.it"),
              string(name: 'STORM_WEBDAV_HOSTNAME', value: "transfer-test.cr.cnaf.infn.it"),
              string(name: 'STORM_GRIDFTP_HOSTNAME', value: "transfer-test.cr.cnaf.infn.it"),
              string(name: 'CDMI_ENDPOINT', value: ""),
              string(name: 'TESTSUITE_EXCLUDE', value: "${params.TESTSUITE_EXCLUDE}"),
              string(name: 'TESTSUITE_SUITE', value: "${params.TESTSUITE_SUITE}"),
              string(name: 'STORM_STORAGE_ROOT_DIR', value: "/storage/gemss_test1"),
            ]
          }
        
          step ([$class: 'CopyArtifact',
            projectName: "${env.JOB_NAME}/${params.TESTSUITE_BRANCH}",
            selector: [$class: 'SpecificBuildSelector', buildNumber: "${runner_job.number}"]
          ])

          archiveArtifacts '**'
          step([$class: 'RobotPublisher',
            disableArchiveOutput: false,
            logFileName: 'log.html',
            otherFiles: '*.png',
            outputFileName: 'output.xml',
            outputPath: "runner/reports-jenkins-storm-testsuite_runner-nightly-${runner_job.number}/reports",
            passThreshold: 100,
            reportFileName: 'report.html',
            unstableThreshold: 90])
        }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }

    changed {
      script {
        if('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
