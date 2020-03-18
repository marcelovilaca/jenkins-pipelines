#!/usr/bin/env groovy

@Library('sd')_
def kubeLabel = getKubeLabel()

def pkg_build_number = 'nightly'
def pkg_el6
def pkg_el7

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
    timeout(time: 3, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers {
    cron('@midnight')
  }

  parameters {
    string(name: 'PKG_TAG_EL6', defaultValue: 'release-el6-1-11-17', description: 'The branch of the EL6 pkg.storm repo' )
    string(name: 'PKG_TAG_EL7', defaultValue: 'release-el7-1-11-18', description: 'The branch of the EL7 pkg.storm repo' )
  }

  environment {
    JOB_NAME = 'pkg.storm'
    NEXUS_URL="http://nexus.default.svc.cluster.local"
  }

  stages {
    stage('create EL6 RPMs') {
      steps {
        script {
          pkg_el6 = build job: "${env.JOB_NAME}/${params.PKG_TAG_EL6}", parameters: [
            string(name: 'INCLUDE_BUILD_NUMBER', value: "1")
          ]
        }
      }
    }
    stage('prepare EL6 RPM repo') {
      steps {
        script {
          step ([$class: 'CopyArtifact',
            projectName: "${env.JOB_NAME}/${params.PKG_TAG_EL6}",
            filter: 'rpms/centos6/**',
            selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el6.number}"]
          ])

          dir('rpms') {
            sh "mkdir -p el6/x86_64"
            sh "mv centos6/*.rpm el6/x86_64/"
            sh "createrepo el6/x86_64/"
            sh "repoview el6/x86_64/"
            stash includes: 'el6/', name: 'rpm6'
          }
        }
      }
    }
    stage('create-repo-file-el6') {
      steps {
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
        stash includes: '*.repo', name: 'repo6'
      }
    }

    stage('create EL7 RPMs') {
      steps {
        script {
          pkg_el7 = build job: "${env.JOB_NAME}/${params.PKG_TAG_EL7}", parameters: [
            string(name: 'INCLUDE_BUILD_NUMBER', value: "1")
          ]
        }
      }
    }
    stage('prepare EL7 RPM repo') {
      steps {
        script {
          step ([$class: 'CopyArtifact',
            projectName: "${env.JOB_NAME}/${params.PKG_TAG_EL7}",
            filter: 'rpms/centos7/**',
            selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el7.number}"]
          ])

          dir('rpms') {
            sh "mkdir -p el7/x86_64"
            sh "mv centos7/*.rpm el7/x86_64/"
            sh "createrepo el7/x86_64/"
            sh "repoview el7/x86_64/"
            stash includes: 'el7/', name: 'rpm7'
          }
        }
      }
    }
    stage('create-repo-file-el7') {
      steps {
        script {
          def repoStr = """[storm-nightly-centos7]
name=storm-nightly-centos7
baseurl=https://repo.cloud.cnaf.infn.it/repository/storm/nightly/el7/x86_64/
protect=1
enabled=1
priority=1
gpgcheck=0
"""
          writeFile file: "storm-nightly-centos7.repo", text: "${repoStr}"
        }
        stash includes: '*.repo', name: 'repo7'
      }
    }

    stage('push EL6 to Nexus') {
      steps {
        deleteDir()
        unstash 'rpm6'
        unstash 'repo6'

        withCredentials([
          usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
        ]) {
          sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q nightly/storm-nightly-centos6.repo"
          sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q nightly/el6"
          sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/nightly -d ."
        }
      }
    }

    stage('push EL7 to Nexus') {
      steps {
        deleteDir()
        unstash 'rpm7'
        unstash 'repo7'

        withCredentials([
          usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
        ]) {
          sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q nightly/storm-nightly-centos7.repo"
          sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q nightly/el7"
          sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/nightly -d ."
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
        if('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
