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
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { 
    upstream(upstreamProjects: 'docker_build-storm-testsuite-image', threshold: hudson.model.Result.SUCCESS)
  }

  environment {
    DOCKER_REGISTRY_HOST = "${env.DOCKER_REGISTRY_HOST}"
    REPOSITORY = "https://github.com/italiangrid/grinder-load-testsuite"
    BRANCH = "develop"
    DIRECTORY = "docker"
  }

  stages {
    stage('prepare'){
      steps {
        deleteDir()
        git url: "${env.REPOSITORY}", branch: "${env.BRANCH}"
        sh "docker pull ${env.DOCKER_REGISTRY_HOST}/italiangrid/storm-testsuite"
        sh "docker tag ${env.DOCKER_REGISTRY_HOST}/italiangrid/storm-testsuite italiangrid/storm-testsuite"
      }
    }

    stage('build') {
      steps {
        dir("${env.DIRECTORY}") { 
          sh "docker build -t ${env.DOCKER_REGISTRY_HOST}/italiangrid/grinder ."
        }
      }
    }

    stage('push') {
      steps {
        dir("${env.DIRECTORY}") { 
          sh "docker push ${env.DOCKER_REGISTRY_HOST}/italiangrid/grinder"
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
