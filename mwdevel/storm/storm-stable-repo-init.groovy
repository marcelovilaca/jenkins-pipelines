pipeline {
  agent none

  options {
    timeout(time: 3, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  environment {
    NEXUS_URL="http://nexus.default.svc.cluster.local" 
  }

  stages {
    stage('download RPMs'){
      agent { label 'generic' }
      steps {
        container('generic-runner'){
          deleteDir()
          sh """
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/emi-storm-backend-mp-1.2.0-4.el6.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/emi-storm-frontend-mp-1.1.0-2.el6.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/emi-storm-globus-gridftp-mp-1.1.0-2.el6.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/emi-storm-gridhttps-mp-1.0.0-3.el6.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/emi-storm-srm-client-mp-1.0.0-5.el6.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-backend-server-1.11.11-1.el6.centos.noarch.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-dynamic-info-provider-1.7.9-1.el6.centos.noarch.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-frontend-server-1.8.9-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-frontend-server-debuginfo-1.8.9-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-globus-gridftp-server-1.2.0-5.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-gridhttps-plugin-1.1.0-4.el6.noarch.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-gridhttps-server-3.0.4-1.el6.centos.noarch.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-native-libs-1.0.4-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-native-libs-debuginfo-1.0.4-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-native-libs-gpfs-1.0.4-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-native-libs-java-1.0.4-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-native-libs-lcmaps-1.0.4-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-srm-client-1.6.1-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-webdav-1.0.4-1.el6.centos.noarch.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-xmlrpc-c-1.33.0-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-xmlrpc-c-apps-1.33.0-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-xmlrpc-c-c%2b%2b-1.33.0-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-xmlrpc-c-client%2b%2b-1.33.0-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-xmlrpc-c-client-1.33.0-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-xmlrpc-c-debuginfo-1.33.0-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/storm-xmlrpc-c-devel-1.33.0-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/versions/storm-master-030717/sl6/x86_64/yaim-storm-4.3.8-1.el6.centos.noarch.rpm

wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/emi-storm-backend-mp-1.2.1-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/storm-backend-server-1.11.12-1.el6.centos.noarch.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/storm-frontend-server-1.8.10-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/storm-frontend-server-debuginfo-1.8.10-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/storm-native-libs-1.0.5-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/storm-native-libs-debuginfo-1.0.5-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/storm-native-libs-gpfs-1.0.5-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/storm-native-libs-java-1.0.5-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/storm-native-libs-lcmaps-1.0.5-1.el6.centos.x86_64.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/storm-webdav-1.0.5-1.el6.centos.noarch.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3.old.2018.02.19/sl6/x86_64/yaim-storm-4.3.9-1.el6.centos.noarch.rpm

wget http://ci-01.cnaf.infn.it/download/storm/emi3/sl6/x86_64/storm-backend-server-1.11.13-1.el6.centos.noarch.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3/sl6/x86_64/storm-dynamic-info-provider-1.8.0-1.el6.centos.noarch.rpm
wget http://ci-01.cnaf.infn.it/download/storm/emi3/sl6/x86_64/yaim-storm-4.3.10-1.el6.centos.noarch.rpm

mkdir -p el6/x86_64
mv *.rpm el6/x86_64

wget http://repo.indigo-datacloud.eu/repository/indigo/2/centos7/x86_64/updates/cdmi-storm-0.1.0-1.el7.centos.noarch.rpm
mkdir -p el7/x86_64
mv cdmi-storm*.rpm el7/x86_64
"""
          sh "createrepo el6/x86_64/"
          sh "repoview el6/x86_64/"
          sh "createrepo el7/x86_64/"
          sh "repoview el7/x86_64/"
          stash includes: 'el6/', name: 'rpm6'
          stash includes: 'el7/', name: 'rpm7'
        }
      }
    }

    stage('create-repo-file') {
      agent { label 'generic' }
      steps {
        container('generic-runner') {
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
    }

    stage('push to Nexus'){
      agent { label 'generic' }
      steps {
        container('generic-runner') {
          deleteDir()
          unstash 'rpm6'
          unstash 'rpm7'
          unstash 'repo6'
          unstash 'repo7'
          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            sh "nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm -q stable/"
            sh "nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r storm/stable -d ."
          }
        }
      }
    }
  }
}
