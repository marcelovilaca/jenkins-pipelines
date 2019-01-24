def BETA_RPM_URLS
def BETA_RPM_NAMES
def STABLE_RPM_URLS

pipeline {
  agent { label 'generic' }

  options {
    timeout(time: 3, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  environment {
    NEXUS_URL="http://nexus.default.svc.cluster.local"
    NEXUS_BASE_URL="https://repo.cloud.cnaf.infn.it"
  }

  stages {
    stage("create local el6 rpm dir") {
      steps {
        container('generic-runner') {
          sh "mkdir -p el6/x86_64"
        }
      }
    }
    stage("download beta el6 rpms") {
      steps {
        container('generic-runner') {
          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            script {
              BETA_RPM_URLS = sh (
                script: "nexus-assets-list -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q beta | grep el6 | grep .rpm",
                returnStdout: true
              )
              echo "BETA_RPM_URLS = ${BETA_RPM_URLS}"
              BETA_RPM_URLS.split('\n').each { relativePath ->
                def rpmUrl = "${env.NEXUS_BASE_URL}${relativePath}"
                echo "Downloading beta rpm ${rpmUrl} ..."
                sh (
                  script: "wget ${rpmUrl} -P el6/x86_64"
                )
              }
              BETA_RPM_NAMES = sh (
                script: "ls el6/x86_64 | grep .rpm",
                returnStdout: true
              )
              echo "BETA_RPM_NAMES = ${BETA_RPM_NAMES}"
            }
          }
        }
      }
    }
    stage("download stable el6 rpms") {
      steps {
        container('generic-runner') {
          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            script {
              STABLE_RPM_URLS = sh (
                script: "nexus-assets-list -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q stable | grep el6 | grep .rpm",
                returnStdout: true
              )
              echo "STABLE_RPM_URLS = ${STABLE_RPM_URLS}"
              STABLE_RPM_URLS.split('\n').each { relativePath ->
                def rpmUrl = "${env.NEXUS_BASE_URL}${relativePath}"
                echo "Downloading stable rpm ${rpmUrl} ..."
                sh (
                  script: "wget ${rpmUrl} -P el6/x86_64"
                )
              }
            }
          }
        }
      }
    }
    stage("generate el6 repoview") {
      steps {
        container('generic-runner') {
          sh "createrepo el6/x86_64/"
          sh "repoview el6/x86_64/"
        }
      }
    }
    stage("upload el6 beta rpms to stable repo") {
      steps {
        container('generic-runner') {
          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            script {
              BETA_RPM_NAMES.split('\n').each { fileName ->
                def rpmPath = "el6/x86_64/${fileName}"
                echo "Uploading local beta rpm ${rpmPath} ..."
                sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/stable/el6/x86_64 -d ${rpmPath}"
              }
            }
          }
        }
      }
    }
    stage("replace el6 stable repo repodata and repoview files") {
      steps {
        container('generic-runner') {
          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            script {
              sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q stable/el6/x86_64/repoview"
              sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q stable/el6/x86_64/repodata"
              sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/stable/el6/x86_64 -d el6/x86_64/repoview"
              sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/stable/el6/x86_64 -d el6/x86_64/repodata"
            }
          }
        }
      }
    }

    stage("create local el7 rpm dir") {
      steps {
        container('generic-runner') {
          sh "mkdir -p el7/x86_64"
        }
      }
    }
    stage("download beta el7 rpms") {
      steps {
        container('generic-runner') {
          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            script {
              BETA_RPM_URLS = sh (
                script: "nexus-assets-list -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q beta | grep el7 | grep .rpm",
                returnStdout: true
              )
              echo "BETA_RPM_URLS = ${BETA_RPM_URLS}"
              BETA_RPM_URLS.split('\n').each { relativePath ->
                def rpmUrl = "${env.NEXUS_BASE_URL}${relativePath}"
                echo "Downloading beta rpm ${rpmUrl} ..."
                sh (
                  script: "wget ${rpmUrl} -P el7/x86_64"
                )
              }
              BETA_RPM_NAMES = sh (
                script: "ls el7/x86_64 | grep .rpm",
                returnStdout: true
              )
              echo "BETA_RPM_NAMES = ${BETA_RPM_NAMES}"
            }
          }
        }
      }
    }
    stage("download stable el7 rpms") {
      steps {
        container('generic-runner') {
          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            script {
              STABLE_RPM_URLS = sh (
                script: "nexus-assets-list -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q stable | grep el7 | grep .rpm",
                returnStdout: true
              )
              echo "STABLE_RPM_URLS = ${STABLE_RPM_URLS}"
              STABLE_RPM_URLS.split('\n').each { relativePath ->
                def rpmUrl = "${env.NEXUS_BASE_URL}${relativePath}"
                echo "Downloading stable rpm ${rpmUrl} ..."
                sh (
                  script: "wget ${rpmUrl} -P el7/x86_64"
                )
              }
            }
          }
        }
      }
    }
    stage("generate el7 repoview") {
      steps {
        container('generic-runner') {
          sh "createrepo el7/x86_64/"
          sh "repoview el7/x86_64/"
        }
      }
    }
    stage("upload el7 beta rpms to stable repo") {
      steps {
        container('generic-runner') {
          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            script {
              BETA_RPM_NAMES.split('\n').each { fileName ->
                def rpmPath = "el7/x86_64/${fileName}"
                echo "Uploading local beta rpm ${rpmPath} ..."
                sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/stable/el7/x86_64 -d ${rpmPath}"
              }
            }
          }
        }
      }
    }
    stage("replace el7 stable repo repodata and repoview files") {
      steps {
        container('generic-runner') {
          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            script {
              sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q stable/el7/x86_64/repoview"
              sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q stable/el7/x86_64/repodata"
              sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/stable/el7/x86_64 -d el7/x86_64/repoview"
              sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/stable/el7/x86_64 -d el7/x86_64/repodata"
            }
          }
        }
      }
    }
  }
}
