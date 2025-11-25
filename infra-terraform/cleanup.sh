#!/usr/bin/env bash

rm .terraform.lock.hcl
rm -rf .terraform
rm terraform.tfstate
rm terraform.tfstate.backup
rm localstack_providers_override.tf