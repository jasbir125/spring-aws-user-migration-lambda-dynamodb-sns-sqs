#!/usr/bin/env bash

mvn clean package

cd infra-terraform

tflocal init

tflocal plan -var-file="local.terraform.tfvars"

tflocal apply -var-file="local.terraform.tfvars" -auto-approve

#tflocal destroy -var-file="local.terraform.tfvars" -auto-approve
