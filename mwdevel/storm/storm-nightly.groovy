#!/usr/bin/env groovy

def pkg_build_number = 'nightly'
def pkg_el6

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
    string(name: 'PKG_TAG', defaultValue: 'release_1_11_14', description: 'The branch of the pkg.storm repo' )
  }

  environment {
    JOB_NAME = 'pkg.storm'
    INCLUDE_BUILD_NUMBER='1'
    NEXUS_URL="http://nexus.default.svc.cluster.local"
  }

  stages {
    stage('create RPM'){
      steps{
        script{
          pkg_el6 = build job: "${env.JOB_NAME}/${params.PKG_TAG}", parameters: [
            string(name: 'PKG_BUILD_NUMBER', value: "${pkg_build_number}"),
            string(name: 'INCLUDE_BUILD_NUMBER', value: "${env.INCLUDE_BUILD_NUMBER}"),
            string(name: 'PLATFORM', value: "centos6")
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
              filter: 'rpms/centos6/**',
              selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el6.number}"]
            ])

            dir('rpms') {
              sh "mkdir -p el6/x86_64"
              sh "mv centos6/*.rpm el6/x86_64/"
              sh "createrepo el6/x86_64/"
              sh "repoview el6/x86_64/"
              stash includes: 'el6/', name: 'rpm'
            }
          }
        }
      }
    }

	stage('create-repo-file') {
      agent { label 'generic' }
      steps {
      	container('generic-runner') {
    	  script {
            def repoStr = """[storm-nightly-centos6]
name=storm-nightly-centos6
baseurl=https://repo.cloud.cnaf.infn.it/repository/storm/nightly/el6/x86_64/
protect=1
enabled=1
priority=1
gpgcheck=0
"""
            writeFile file: "storm-nightly-centos6.repo", text: "${repoStr}"
          }
          stash includes: '*.repo', name: 'repo'
        }
      }
    }

    stage('push to Nexus'){
      agent { label 'generic' }
      steps {
      	container('generic-runner'){
		  deleteDir()
		  unstash 'rpm'
		  unstash 'repo'

          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q nightly/"
            sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/nightly -d ."
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
