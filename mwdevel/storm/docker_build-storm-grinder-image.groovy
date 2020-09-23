#!/usr/bin/env groovy
pipeline {

  agent { label 'docker' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { 
    upstream(upstreamProjects: 'docker_build-storm-testsuite-image', threshold: hudson.model.Result.SUCCESS)
  }

  environment {
    REPOSITORY = "https://github.com/italiangrid/grinder-load-testsuite"
    BRANCH = "develop"
  }

  stages {
    stage('prepare'){
      steps {
        deleteDir()
        git url: "${env.REPOSITORY}", branch: "${env.BRANCH}"
      }
    }

    stage('build') {
      steps {
        dir('docker') {
          sh "sh build-image.sh"
        }
      }
    }

    stage('push-dockerhub') {
      steps {
        script {
          withDockerRegistry([ credentialsId: "dockerhub-enrico", url: "" ]) {
            dir('docker') {
              sh "sh push-image-dockerhub.sh"
            }
          }
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
        if ('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
