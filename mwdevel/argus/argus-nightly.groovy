#!/usr/bin/env groovy

def build_number, pkg_el6, pkg_el7, job_to_build

pipeline {
  agent { label 'generic' }
	
  triggers {
    cron('@midnight')
  }

  options {
    timeout(time: 3, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }
	
  parameters {
    string(name: 'PKG_TAG', defaultValue: 'release/1.7.2', description: 'The branch of the pkg.argus repo' )
  }
	
  environment {
    COMPONENT_LIST='pap pdp-pep-common pep-common pdp pep-server pep-api-c pep-api-java pepcli gsi-pep-callout metapackage'
    INCLUDE_BUILD_NUMBER='1'
    USE_DOCKER_REGISTRY='1'
    NEXUS_URL="http://nexus.default.svc.cluster.local" 
  }
	
  stages {
    stage('prepare'){
      steps {
        script{
          def branch = java.net.URLEncoder.encode("${params.PKG_TAG}", "UTF-8")
          job_to_build = "argus-authz/pkg.argus/${branch}"
          build_number = new Date().format("yyyyMMddHHmmss")
        }
      }
    }
	
    stage('build'){
      steps{
        script{
         parallel(
           'centos6': {
             pkg_el6 = build job: "${job_to_build}", parameters: [
                string(name: 'COMPONENT_LIST', value: "${env.COMPONENT_LIST}"),
                string(name: 'PLATFORM', value: 'centos6'),
                string(name: 'INCLUDE_BUILD_NUMBER', value: "${env.INCLUDE_BUILD_NUMBER}"),
                string(name: 'PKG_BUILD_NUMBER', value: "${build_number}"),
                string(name: 'USE_DOCKER_REGISTRY', value: "${env.USE_DOCKER_REGISTRY}")
              ]
           },
           'centos7': {
             pkg_el7 = build job: "${job_to_build}", parameters: [
               string(name: 'COMPONENT_LIST', value: "${env.COMPONENT_LIST}"),
               string(name: 'PLATFORM', value: 'centos7'),
               string(name: 'INCLUDE_BUILD_NUMBER', value: "${env.INCLUDE_BUILD_NUMBER}"),
               string(name: 'PKG_BUILD_NUMBER', value: "${build_number}"),
               string(name: 'USE_DOCKER_REGISTRY', value: "${env.USE_DOCKER_REGISTRY}")
             ]
            }
          )
        }
      }
    }

    stage('archive'){
      steps {
        container('generic-runner'){
          script {
            step ([$class: 'CopyArtifact',
              projectName: "${job_to_build}",
              filter: 'repo/centos6/**',
              selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el6.number}"]
            ])

            step ([$class: 'CopyArtifact',
              projectName: "${job_to_build}",
              filter: 'repo/centos7/**',
              selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el7.number}"]
            ])

            dir('repo') {
              sh "mkdir -p {el6,el7}"
              sh "mv centos6/* el6/"
              sh "repoview el6/"
           
              sh "mv centos7/* el7/"
              sh "repoview el7/"
              sh "rm -rfv centos6/ centos7/"
           
              stash includes: '**', name: 'rpms'
            }
          }
        }
      }
    }
    
    stage('push to Nexus'){
      steps {
      	container('generic-runner'){
      	  deleteDir()
		  unstash 'rpms'

          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r argus -q nightly/"
            sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r argus/nightly -d ."
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
