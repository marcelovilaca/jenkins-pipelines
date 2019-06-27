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
    timeout(time: 3, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  environment {
    STORM_STABLE_REPO_NAME="storm_test"
    NEXUS_URL="http://nexus.default.svc.cluster.local"
    NEXUS_BASE_URL="https://repo.cloud.cnaf.infn.it"
  }

  stages {
    stage("import backup rpms") {
      steps {
        script {
          step ([$class: 'CopyArtifact',
            projectName: "storm-stable-repo-backup",
            selector: lastSuccessful()
          ])
        }
      }
    }
    stage("generate el6 repoview") {
      steps {
        sh "createrepo el6/x86_64/"
        sh "repoview el6/x86_64/"
        stash includes: 'el6/', name: 'rpm6'
      }
    }
    stage("generate el7 repoview") {
      steps {
        sh "createrepo el7/x86_64/"
        sh "repoview el7/x86_64/"
        stash includes: 'el7/', name: 'rpm7'
      }
    }
    stage('create-repo-files') {
      steps {
        script {
          def repoStr = """[storm-stable-centos6]
name=storm-stable-centos6
baseurl=https://repo.cloud.cnaf.infn.it/repository/storm/stable/el6/x86_64/
protect=1
enabled=1
priority=1
gpgcheck=0
"""
          writeFile file: "storm-stable-centos6.repo", text: "${repoStr}"
        }
        script {
          def repoStr = """[storm-stable-centos7]
name=storm-stable-centos7
baseurl=https://repo.cloud.cnaf.infn.it/repository/storm/stable/el7/x86_64/
protect=1
enabled=1
priority=1
gpgcheck=0
"""
          writeFile file: "storm-stable-centos7.repo", text: "${repoStr}"
        }
        stash includes: 'storm-stable-centos6.repo', name: 'repo6'
        stash includes: 'storm-stable-centos7.repo', name: 'repo7'
      }
    }
    stage('push to Nexus') {
      steps {
        deleteDir()
        unstash 'rpm6'
        unstash 'rpm7'
        unstash 'repo6'
        unstash 'repo7'
        withCredentials([
          usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
        ]) {
          sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r ${env.STORM_STABLE_REPO_NAME} -q stable/"
          sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r ${env.STORM_STABLE_REPO_NAME}/stable -d ."
        }
      }
    }
  }
}
