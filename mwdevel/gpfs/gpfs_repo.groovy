#!/usr/bin/env groovy

pipeline {
  agent { label 'generic' }

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
        container('generic-runner') {
          cleanWs()
          sh """

mkdir yum

wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.base-3.4.0-17.x86_64.rpm 
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.msg.en_US-3.4.0-17.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.docs-3.4.0-17.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gpl-3.4.0-17.noarch.rpm

wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.base-4.1.1-12.x86_64.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.msg.en_US-4.1.1-12.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.docs-4.1.1-12.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gpl-4.1.1-12.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.ext-4.1.1-12.x86_64.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gplbin-2.6.32-504.8.1.el6.x86_64-4.1.1-12.x86_64.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gplbin-3.10.0-327.36.3.el7.x86_64-4.1.1-12.x86_64.rpm

wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.base-4.1.1-14.x86_64.rpm 
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.msg.en_US-4.1.1-14.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.docs-4.1.1-14.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gpl-4.1.1-14.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.ext-4.1.1-14.x86_64.rpm

wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gskit-8.0.50-47.x86_64.rpm

wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.base-4.1.1-16.x86_64.rpm 
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.msg.en_US-4.1.1-16.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.docs-4.1.1-16.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.gpl-4.1.1-16.noarch.rpm
wget -P yum http://os-server.cnaf.infn.it/distro/Storage/GPFS-pkg/gpfs.ext-4.1.1-16.x86_64.rpm

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
    }
    stage('push to Nexus'){
      steps {
        container('generic-runner'){
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
}