#!/usr/bin/env groovy

def build_image(platform, deployment){
  node('docker') {
    deleteDir()
    unstash "source"

    dir("${deployment}") {
      sh "PLATFORM=${platform} sh build-images.sh"
      sh "PLATFORM=${platform} sh push-images.sh"
    }
  }
}

pipeline {
  agent any

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
    timeout(time: 4, unit: 'HOURS')
  }

  triggers { cron('@daily') }
  
  environment {
    DOCKER_REGISTRY_HOST = "${env.DOCKER_REGISTRY_HOST}"
  }

  stages {
    stage('prepare') {
      steps {
        git 'https://github.com/argus-authz/argus-deployment-test.git'
        stash name: "source"
      }
    }

    stage('build images') {
      steps {
        parallel (
          "centos6-allinone"   : { build_image('centos6', 'all-in-one') },
          "centos6-distributed": { build_image('centos6', 'distributed') },
          "centos7-allinone"   : { build_image('centos7', 'all-in-one') },
          "centos7-distributed": { build_image('centos7', 'distributed') },
        )
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
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
