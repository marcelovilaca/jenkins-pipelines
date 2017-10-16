#!/usr/bin/env groovy

def pkg_build_number = ''
def pkg_el7, pkg_deb

pipeline {
  agent none
	
  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }
    
  triggers {
    cron('@daily')
  }
    
  parameters {
    string(name: 'PKG_TAG', defaultValue: 'v1.1.0', description: 'The branch of the pkg.argus repo' )
    choice(name: 'INCLUDE_BUILD_NUMBER', choices: '1\n0', description: 'Flag to include/exclude build number')
  }
    
  environment {
    JOB_NAME = 'indigo-iam/pkg.indigo-iam'
    IAM_ROOT = "/mnt/packages/repo/indigo-iam"
  }
    
  stages {
    stage('prepare'){
    agent { label 'generic' }
      steps {
        script {
          if("${params.INCLUDE_BUILD_NUMBER}" == "1") {
            pkg_build_number = new Date().format("yyyyMMddHHmmss")
          }
        }
      }
    }
      
    stage('create RPM'){
      steps{
        script{
          pkg_el7 = build job: "${env.JOB_NAME}/${params.PKG_TAG}", parameters: [
            string(name: 'PKG_BUILD_NUMBER', value: "${pkg_build_number}"),
            string(name: 'INCLUDE_BUILD_NUMBER', value: "${params.INCLUDE_BUILD_NUMBER}"),
            string(name: 'PLATFORM', value: "centos7")
          ]
        }
      }
    }
      
    stage('create DEB'){
      steps {
        script {
          pkg_deb = build job: "${env.JOB_NAME}/${params.PKG_TAG}", parameters: [
	    string(name: 'PKG_BUILD_NUMBER', value: "${pkg_build_number}"),
	    string(name: 'INCLUDE_BUILD_NUMBER', value: "${params.INCLUDE_BUILD_NUMBER}"),
	    string(name: 'PLATFORM', value: "ubuntu1604")
	  ]
        }
      }
    }
      
    stage('archive RPMs'){
      agent { label 'generic' }
      steps {
        script {
          step ([$class: 'CopyArtifact',
	    projectName: "${env.JOB_NAME}/${params.PKG_TAG}",
	    filter: 'repo/centos7/**',
	    selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el7.number}"]
	  ])
		
	  dir('repo') {
	    sh 'mkdir -p el7/RPMS'
	    sh 'mv centos7/* el7/RPMS/'
	    sh "createrepo el7/RPMS/"
	    sh "repoview el7/RPMS/"
	    sh "mkdir -p ${env.IAM_ROOT}/builds/build_${BUILD_NUMBER}"
	    sh "cp -r el7/ ${env.IAM_ROOT}/builds/build_${BUILD_NUMBER}/"
	  }
        }
      }
    }

    stage('archive DEBs'){
      agent { label 'generic-ubuntu' }
      steps {
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
	    dir('xenial') { sh "dpkg-scanpackages -m amd64 | gzip > amd64/Packages.gz" }
	    sh "mkdir -p ${env.IAM_ROOT}/builds/build_${env.BUILD_NUMBER}"
	    sh "cp -r xenial/ ${env.IAM_ROOT}/builds/build_${env.BUILD_NUMBER}/"
	  }
      	}
      }
    }
    
    stage('update symlink'){
      agent { label 'generic' }
      steps {
        sh "rm -vf ${env.IAM_ROOT}/nightly"
        dir("${env.IAM_ROOT}"){ sh "ln -vs ./builds/build_${env.BUILD_NUMBER}/ nightly" }
        sh "find ${iam_root}/builds/ -maxdepth 1 -type d -ctime +10 -print -exec rm -rf {} \\;"
      }
    }
  
    stage('result'){
      steps {
        script {
      	  currentBuild.result = 'SUCCESS'
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
        if('SUCCESS'.equals(currentBuild.result)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
