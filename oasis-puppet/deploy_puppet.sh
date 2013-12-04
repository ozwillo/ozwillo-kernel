#!/bin/bash

PUPPET_PARENT_PATH='/project'
PUPPET_PATH="${PUPPET_PARENT_PATH}/puppet"
SSH_CONNECT='root@atolcd-oasis-demo.hosting.atolcd.priv'
MANIFEST_FILE='oasis-demo.pp'
OASIS_VERSION='0.5.0-SNAPSHOT'
#OASIS_VERSION_TYPE='releases'
OASIS_VERSION_TYPE='snapshots'
ARCHIVE_FILE="puppet-${OASIS_VERSION}.zip"

## copy puppet packaged archive
# scp "target/${ARCHIVE_FILE}" "${SSH_CONNECT}:/var/tmp"
# ssh ${SSH_CONNECT} "rm -rf ${PUPPET_PATH}; unzip -d ${PUPPET_PARENT_PATH} /var/tmp/${ARCHIVE_FILE}"

## download from nexus
nexus_url="http://nexus.atolcd.priv/service/local/artifact/maven/content?g=fr.pole-numerique.oasis&a=oasis-puppet&v=${OASIS_VERSION}&r=${OASIS_VERSION_TYPE}&p=zip"
ssh ${SSH_CONNECT} "rm -rf ${PUPPET_PATH}; wget ${nexus_url} -O ${PUPPET_PATH}"


## rsync
# rsync -avz --delete --delete-excluded --exclude '.tmp' --exclude '.librarian' --exclude '.git' --exclude 'vagrant' --exclude '.gitignore' . -e ssh ${SSH_CONNECT}:${PUPPET_PATH}/

ssh ${SSH_CONNECT} "puppet apply --modulepath '${PUPPET_PATH}/modules/deps/:${PUPPET_PATH}/modules/project/' --detailed-exitcodes ${PUPPET_PATH}/manifests/${MANIFEST_FILE}"

