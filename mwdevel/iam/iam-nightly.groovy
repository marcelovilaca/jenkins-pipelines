#!/usr/bin/env groovy

def pkg_el7, pkg_deb

pipeline {
  agent none

  options {
    timeout(time: 3, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers {
    cron('@midnight')
  }

  parameters {
    string(name: 'PKG_TAG', defaultValue: 'develop', description: 'The branch of the pkg.argus repo' )
  }

  environment {
  	BUILD_NUMBER = 'nightly'
    JOB_NAME = 'indigo-iam/pkg.indigo-iam'
    INCLUDE_BUILD_NUMBER='1'
    NEXUS_URL="http://nexus.default.svc.cluster.local" 
  }

  stages {
    stage('create RPM'){
      steps{
        script{
          pkg_el7 = build job: "${env.JOB_NAME}/${params.PKG_TAG}", parameters: [
            string(name: 'PKG_BUILD_NUMBER', value: "${env.BUILD_NUMBER}"),
            string(name: 'INCLUDE_BUILD_NUMBER', value: "${env.INCLUDE_BUILD_NUMBER}"),
            string(name: 'PLATFORM', value: "centos7")
          ]
        }
      }
    }

    stage('create DEB'){
      steps {
        script {
          pkg_deb = build job: "${env.JOB_NAME}/${params.PKG_TAG}", parameters: [
            string(name: 'PKG_BUILD_NUMBER', value: "${env.BUILD_NUMBER}"),
            string(name: 'INCLUDE_BUILD_NUMBER', value: "${env.INCLUDE_BUILD_NUMBER}"),
            string(name: 'PLATFORM', value: "ubuntu1604")
		  ]
        }
      }
    }

    stage('prepare RPM repo'){
      agent { label 'generic' }
      steps {
        container('generic-runner'){
          script {
            step ([$class: 'CopyArtifact',
              projectName: "${env.JOB_NAME}/${params.PKG_TAG}",
              filter: 'repo/centos7/**',
              selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el7.number}"]
            ])

            dir('repo') {
              sh "mkdir -p el7/RPMS"
              sh "mv centos7/* el7/RPMS/"
              sh "createrepo el7/RPMS/"
              sh "repoview el7/RPMS/"

              stash includes: 'el7/', name: 'rpm'
            }
          }
        }
      }
    }

    stage('prepare DEB repo'){
      agent { label 'generic-ubuntu' }
      steps {
        container('ubuntu-runner'){
      	  script {
            step ([$class: 'CopyArtifact',
              projectName: "${env.JOB_NAME}/${params.PKG_TAG}",
              filter: 'repo/**',
              selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_deb.number}"]
            ])

            dir('repo') {
              def debdir = "xenial/amd64"
              sh "mkdir -p ${debdir}"
              sh "mv ubuntu1604/*.deb ${debdir}"
              dir('xenial') {
                sh "dpkg-scanpackages -m amd64 | gzip > amd64/Packages.gz"
              }

              stash includes: 'xenial/', name: 'deb'
            }
          }
      	}
      }
    }

    stage('push to Nexus'){
      agent { label 'generic' }
      steps {
      	container('generic-runner'){
		  deleteDir()
		  unstash 'rpm'
		  unstash 'deb'

          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r indigo-iam -q nightly/"
            sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r indigo-iam/nightly -d ."
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
      script{
        if('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
