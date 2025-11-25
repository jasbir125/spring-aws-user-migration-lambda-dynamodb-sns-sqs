#!/usr/bin/env bash

mvn clean package

cd infra-terraform

#tflocal destroy -var-file="terraform.tfvars" -auto-approve

tflocal init

#tflocal plan -var-file="terraform.tfvars"

tflocal apply -var-file="terraform.tfvars" -auto-approve
