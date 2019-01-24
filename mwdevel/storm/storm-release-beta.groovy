#!/usr/bin/env groovy

def pkg_el6
def pkg_el7

pipeline {
  agent none

  options {
    timeout(time: 3, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    string(name: 'PKG_TAG_EL6', defaultValue: 'release-el6-1-11-15', description: 'The branch of the EL6 pkg.storm repo')
    string(name: 'PKG_TAG_EL7', defaultValue: 'release-el7-1-11-15', description: 'The branch of the EL7 pkg.storm repo')
    booleanParam(name: 'REBUILD_PKG_EL6', defaultValue: true, description: 'Rebuild the branch of the EL6 pkg.storm repo before copying artifacts')
    booleanParam(name: 'REBUILD_PKG_EL7', defaultValue: true, description: 'Rebuild the branch of the EL7 pkg.storm repo before copying artifacts')
  }

  environment {
    JOB_NAME = 'pkg.storm'
    NEXUS_URL="http://nexus.default.svc.cluster.local"
  }

  stages {
    stage('create EL6 RPMs') {
      when {
        expression {
          return params.REBUILD_PKG_EL6;
        }
      }
      steps {
        script {
          pkg_el6 = build job: "${env.JOB_NAME}/${params.PKG_TAG_EL6}", parameters: [
            string(name: 'INCLUDE_BUILD_NUMBER', value: "")
          ]
        }
      }
    }

    stage('prepare EL6 RPM repo') {
      agent { label 'generic' }
      steps {
        container('generic-runner') {
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
    }

    stage('create EL6 repo file') {
      agent { label 'generic' }
      steps {
        container('generic-runner') {
        script {
            def repoStr = """[storm-beta-centos6]
name=storm-beta-centos6
baseurl=https://repo.cloud.cnaf.infn.it/repository/storm/beta/el6/x86_64/
protect=1
enabled=1
priority=1
gpgcheck=0
"""
            writeFile file: "storm-beta-centos6.repo", text: "${repoStr}"
          }
          stash includes: '*.repo', name: 'repo6'
        }
      }
    }

    stage('create EL7 RPMs') {
      when {
        expression {
          return params.REBUILD_PKG_EL7;
        }
      }
      steps {
        script {
          pkg_el7 = build job: "${env.JOB_NAME}/${params.PKG_TAG_EL7}", parameters: [
            string(name: 'INCLUDE_BUILD_NUMBER', value: "")
          ]
        }
      }
    }

    stage('prepare EL7 RPM repo') {
      agent { label 'generic' }
      steps {
        container('generic-runner') {
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
    }

    stage('create EL7 repo file') {
      agent { label 'generic' }
      steps {
        container('generic-runner') {
        script {
            def repoStr = """[storm-beta-centos7]
name=storm-beta-centos7
baseurl=https://repo.cloud.cnaf.infn.it/repository/storm/beta/el7/x86_64/
protect=1
enabled=1
priority=1
gpgcheck=0
"""
            writeFile file: "storm-beta-centos7.repo", text: "${repoStr}"
          }
          stash includes: '*.repo', name: 'repo7'
        }
      }
    }

    stage('push to Nexus') {
      agent { label 'generic' }
      steps {
        container('generic-runner') {
          deleteDir()
          unstash 'rpm6'
          unstash 'repo6'
          unstash 'rpm7'
          unstash 'repo7'

          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q beta/"
            sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/beta -d ."
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
