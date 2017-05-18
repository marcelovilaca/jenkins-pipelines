#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
])


def approver
def title = 'Indigo IAM Login Service'
def target = 'stable'
def pkg_root = '/mnt/packages/repo/indigo-iam'

try {
  stage("Promote from Beta to Stable") {
    node('generic'){
      dir("${pkg_root}"){
        sh "rsync -avu beta/ ${target}/"

        dir("${target}/") {
          sh "createrepo el7/RPMS"
          sh "repoview -t '${title} (CentOS 7)' el7/RPMS"
        }
      }
    }

    node('generic-ubuntu'){
      dir("${pkg_root}/${target}/xenial"){ sh "dpkg-scanpackages -m amd64 | gzip > amd64/Packages.gz" }
    }
  }
}catch(e) {
  slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
  throw(e)
}
