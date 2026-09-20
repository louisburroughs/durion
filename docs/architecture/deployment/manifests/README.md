---
type: Index
title: Deployment Manifests
description: 'Index of the versioned tenant-cell deployment manifests and their design notes, starting with the integration cell on the current AWS Docker host. These are the first machine-readable statements of a cell runtime structure, not the final deployment automation format.'
status: draft
---

# Deployment Manifests

This directory contains example and reference deployment manifests for Durion tenant cells.

## Documents

- [Integration Tenant Cell Manifest](./integration-tenant-cell.aws-docker-host.yaml) - First reference manifest for the shared integration cell running on the current AWS Docker host
- [Integration Tenant Cell Notes](./integration-tenant-cell.aws-docker-host.md) - Design notes, assumptions, and mapping guidance for the first integration manifest

## Intent

These manifests are not yet the final deployment automation format. They are the first versioned deployment definitions used to make runtime structure explicit and to prepare for release-driven CI/CD.
