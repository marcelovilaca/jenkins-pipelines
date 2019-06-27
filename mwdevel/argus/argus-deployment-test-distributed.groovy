#!/usr/bin/env groovy

@Library('sd')_
def kubeLabel = getKubeLabel()

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
    timeout(time: 2, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    choice (name: 'PLATFORM', choices: 'centos7\ncentos6', description: 'OS platform.')
    string (name: 'TESTSUITE_REPO', defaultValue: 'https://github.com/argus-authz/argus-robot-testsuite', description: '' )
    string (name: 'TESTSUITE_BRANCH', defaultValue: 'master', description: '')
    choice (name: 'REPO', choices: 'ci\nbeta\nstable', description: 'Repository where download Argus RPMs.')
    choice (name: 'GH_REPO', choices: 'staging\nproduction', description: 'Github repository holding Argus RPMs.')
  }
  
  environment {
    PLATFORM = "${params.PLATFORM}"
    TESTSUITE_REPO = "${params.TESTSUITE_REPO}"
    TESTSUITE_BRANCH = "${params.TESTSUITE_BRANCH}"
    DOCKER_REGISTRY_HOST = "${env.DOCKER_REGISTRY_HOST}"
  }

  stages {
    stage('run') {
      steps {
        git 'https://github.com/marcocaberletti/argus-deployment-test.git'
        script {
          def url = ''

          if("${params.REPO}" != "ci") {
            def gh_repo="https://marcocaberletti.github.io"
            def version="${params.PLATFORM}".replace("centos", "el")

            if("${params.GH_REPO}".equals("production")) {
              gh_repo="https://argus-authz.github.io"
            }

            url = "${gh_repo}/repo/${params.REPO}/${version}/RPMS/"
          }

          dir('distributed') {
            sh "export FACTER_ARGUS_REPO_BASE_URL=${url}"
            sh "./deploy.sh"
          }
        }
      }
    }
    
    stage('publish report') {
      steps {
        script {
          step([$class: 'RobotPublisher',
            disableArchiveOutput: false,
            logFileName: 'log.html',
            otherFiles: '',
            outputFileName: 'output.xml',
            outputPath: "distributed/argus_reports/reports",
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

    unstable {
      slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Unstable (<${env.BUILD_URL}|Open>)"
    }

    changed {
      script {
        if ('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}