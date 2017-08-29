#!/usr/bin/env groovy

pipeline{
  agent none

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    choice(name: 'PRODUCT', choices: 'argus\nindigo-iam', description: 'Product packages')
    string(name: 'BUILD_NUMBER', defaultValue: '', description: 'Build to promote. Empty for LastStableBuild' )
    choice(name: 'TARGET', choices: 'beta\nstable', description: 'Target version')
    string(name: 'REPO_TITLE', defaultValue: '', description: 'Description of the repository in a few word. Platform is appended by default.')
  }

  stages {
    stage('promote'){
      agent { label 'generic' }
      steps {
        script {
          def pkg_root = "/mnt/packages/repo/${params.PRODUCT}"
          def dest_dir = "${pkg_root}/${params.TARGET}"
          def src_dir = "${pkg_root}/nightly"

          if ("" != "${params.BUILD_NUMBER}") {
            src_dir = "${pkg_root}/builds/build_${params.BUILD_NUMBER}"
          }

          sh "rsync -av --delete ${src_dir}/ ${dest_dir}/"
        }
      }
    }

    stage('rebuild RPMs repo'){
      agent { label 'generic' }
      steps {
        script {
          def dest_dir = "/mnt/packages/repo/${params.PRODUCT}/${params.TARGET}"

          if(fileExists("${dest_dir}/el6")) {
            sh "createrepo ${dest_dir}/el6/RPMS"
            sh "repoview -t '${params.REPO_TITLE} (CentOS 6)' ${dest_dir}/el6/RPMS"
          }

          if(fileExists("${dest_dir}/el7")) {
            sh "createrepo ${dest_dir}/el7/RPMS"
            sh "repoview -t '${params.REPO_TITLE} (CentOS 7)' ${dest_dir}/el7/RPMS"
          }
        }
      }
    }

    stage('rebuild DEBs repo'){
      agent { label 'generic-ubuntu' }
      steps {
        script {
          def dest_dir = "/mnt/packages/repo/${params.PRODUCT}/${params.TARGET}"

          if(fileExists("${dest_dir}/xenial")) {
            sh """
              cd ${dest_dir}/xenial
              dpkg-scanpackages -m amd64 | gzip > amd64/Packages.gz
            """
          }
        }
      }
    }

    stage('result'){
      steps {
        script { currentBuild.result = 'SUCCESS' }
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
