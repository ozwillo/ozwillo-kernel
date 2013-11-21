#!/bin/bash

PUPPET_PATH='/project/puppet'
SSH_CONNECT='root@atolcd-oasis-demo.hosting.atolcd.priv'
MANIFEST_FILE='oasis-demo.pp'

rsync -avz --delete --delete-excluded --exclude '.tmp' --exclude '.librarian' --exclude '.git' --exclude 'vagrant' --exclude '.gitignore' . -e ssh ${SSH_CONNECT}:${PUPPET_PATH}/

ssh ${SSH_CONNECT} "puppet apply --modulepath '${PUPPET_PATH}/modules/deps/:${PUPPET_PATH}/modules/project/' --detailed-exitcodes ${PUPPET_PATH}/manifests/${MANIFEST_FILE}"

