@Library('sd')_
def kubeLabel = getKubeLabel()

def STABLE_RPM_URLS

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

  environment {
    NEXUS_URL="http://nexus.default.svc.cluster.local"
    NEXUS_BASE_URL="https://repo.cloud.cnaf.infn.it"
  }

  stages {
    stage("create local el6 rpm dir") {
      steps {
        sh "mkdir -p el6/x86_64"
      }
    }
    stage("download stable el6 rpms") {
      steps {
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
            archiveArtifacts 'el6/**'
          }
        }
      }
    }
    stage("create local el7 rpm dir") {
      steps {
        sh "mkdir -p el7/x86_64"
      }
    }
    stage("download stable el7 rpms") {
      steps {
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
            archiveArtifacts 'el7/**'
          }
        }
      }
    }
  }
}
