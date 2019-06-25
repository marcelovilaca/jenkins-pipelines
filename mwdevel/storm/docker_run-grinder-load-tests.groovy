#!/usr/bin/env groovy

@Library('sd')_
def kubeLabel = getKubeLabel()

def image, name, envvars

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
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@daily') }

  environment {
    DOCKER_REGISTRY_HOST = "${env.DOCKER_REGISTRY_HOST}"
  }

  parameters {
    string(defaultValue: "git://github.com/italiangrid/grinder-load-testsuite.git", description: '', name: 'TESTSUITE_REPO')
    choice(choices: 'develop\nmaster', description: '', name: 'TESTSUITE_BRANCH')
    string(defaultValue: "test.vo", description: '', name: 'PROXY_VONAME')
    string(defaultValue: "test0", description: '', name: 'PROXY_USER')
    string(defaultValue: "2", description: '', name: 'GRINDER_PROCESSES')
    string(defaultValue: "10", description: '', name: 'GRINDER_THREADS')
    string(defaultValue: "100", description: '', name: 'GRINDER_RUNS')
    booleanParam(defaultValue: false, description: '', name: 'GRINDER_CONSOLE_USE')
    string(defaultValue: "localhost", description: '', name: 'GRINDER_CONSOLE_HOST')
    choice(choices: 'rm_multi\nrm_test\nmkrmdir_test\nptg_test\nptp_test\nptp_pd\nls_test\nft_in\nft_out\nmixdav\natlas_renaming\natlas_nested', description: '', name: 'GRINDER_TEST')
    string(defaultValue: "omii006-vm03.cnaf.infn.it:8444", description: '', name: 'COMMON_STORM_FE_ENDPOINT_LIST')
    string(defaultValue: "omii006-vm03.cnaf.infn.it:8443", description: '', name: 'COMMON_STORM_DAV_ENDPOINT_LIST')
    string(defaultValue: "test.vo", description: '', name: 'COMMON_TEST_STORAGEAREA')
    choice(choices: "INFO\nWARN\nERROR\nDEBUG", description: '', name: 'LOGGING_LEVEL')
  }

  stages {
    stage ('prepare') {
      steps {
        script {
          image = "italiangrid/grinder:latest"
          echo "image: ${image}"
          name = "${env.JOB_BASE_NAME}-${env.BUILD_NUMBER}"
          echo "name: ${name}"
          def variables = []
          variables.add("-e TESTSUITE_REPO=${params.TESTSUITE_REPO}")
          variables.add("-e TESTSUITE_BRANCH=${params.TESTSUITE_BRANCH}")
          variables.add("-e PROXY_VONAME=${params.PROXY_VONAME}")
          variables.add("-e PROXY_USER=${params.PROXY_USER}")
          variables.add("-e GRINDER_PROCESSES=${params.GRINDER_PROCESSES}")
          variables.add("-e GRINDER_THREADS=${params.GRINDER_THREADS}")
          variables.add("-e GRINDER_RUNS=${params.GRINDER_RUNS}")
          variables.add("-e GRINDER_CONSOLE_USE=${params.GRINDER_CONSOLE_USE}")
          variables.add("-e GRINDER_CONSOLE_HOST=${params.GRINDER_CONSOLE_HOST}")
          variables.add("-e GRINDER_TEST=${params.GRINDER_TEST}")
          variables.add("-e COMMON_STORM_FE_ENDPOINT_LIST=${params.COMMON_STORM_FE_ENDPOINT_LIST}")
          variables.add("-e COMMON_STORM_DAV_ENDPOINT_LIST=${params.COMMON_STORM_DAV_ENDPOINT_LIST}")
          variables.add("-e COMMON_TEST_STORAGEAREA=${params.COMMON_TEST_STORAGEAREA}")
          variables.add("-e LOGGING_LEVEL=${params.LOGGING_LEVEL}")
          envvars = variables.join(' ')
          echo "env-vars: ${envvars}"

          sh "docker pull ${image}"
        }
      }
    }

    stage ('run'){
      steps {
        script {
            sh "docker run --name ${name} ${envvars} ${image}"
            sh "docker logs ${name}>grinder.log"
            archive 'grinder.log'
        }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }

    changed {
      script{
        if('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
