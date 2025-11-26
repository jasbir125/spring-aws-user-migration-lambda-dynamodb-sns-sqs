#!/usr/bin/env bash

mvn clean package

cd infra-terraform

terraform init --reconfigure

terraform plan -var-file="aws.terraform.tfvars"

terraform apply -var-file="aws.terraform.tfvars" -auto-approve

#terraform destroy -var-file="aws.terraform.tfvars" -auto-approve