---
name: format-java
description: Format a backend module's Java code using the Spotless Maven plugin.
---

Format pos-{module} using Spotless:
```bash
./mvnw com.diffplug.spotless:spotless-maven-plugin:2.43.0:apply -Dspotless.java.removeUnusedImports=true -pl pos-{module}
```
