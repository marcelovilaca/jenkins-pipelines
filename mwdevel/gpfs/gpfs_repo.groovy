#!/usr/bin/env groovy

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
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  environment {
    NEXUS_URL="http://nexus.default.svc.cluster.local"
  }

  stages {
    stage('create repo') {
      steps {
        cleanWs()
        sh """

mkdir yum

wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.base-4.2.0-3.x86_64.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.msg.en_US-4.2.0-3.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.docs-4.2.0-3.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gpl-4.2.0-3.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.ext-4.2.0-3.x86_64.rpm

wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.base-4.2.3-4.x86_64.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.msg.en_US-4.2.3-4.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.docs-4.2.3-4.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gpl-4.2.3-4.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.ext-4.2.3-4.x86_64.rpm

wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gskit-8.0.50-57.x86_64.rpm

wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.base-4.2.3-22.x86_64.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.msg.en_US-4.2.3-22.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.docs-4.2.3-22.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gpl-4.2.3-22.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.ext-4.2.3-22.x86_64.rpm

wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gskit-8.0.50-75.x86_64.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gskit-8.0.50-86.x86_64.rpm

ls yum

createrepo -v yum

echo -n "[GPFS]
name=GPFS
baseurl=$JOB_URL/lastSuccessfulBuild/artifact/yum/
protect=1
enabled=1
priority=1
gpgcheck=0" > gpfs.repo

        """
        archiveArtifacts "yum/*.rpm, yum/repodata/*, gpfs.repo"
      }
    }
    stage('push to Nexus') {
      steps {
        sh "rm -rf yum/repodata gpfs.repo"
        sh """
echo -n "[GPFS]
name=GPFS
baseurl=https://repo.cloud.cnaf.infn.it/repository/gpfs/yum/
protect=1
enabled=1
priority=1
gpgcheck=0" > gpfs.repo
"""
        sh "ls -l"
        withCredentials([
          usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
        ]) {
          sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r gpfs -q /"
          sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r gpfs -d ."
        }
      }
    }
  }
}
