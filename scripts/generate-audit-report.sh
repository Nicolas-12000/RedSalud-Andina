#!/usr/bin/env bash
# Regenera docs/audit/who-read-report.json a partir de la evidencia fija de AuditReportGoldenTests.
# Ejecútalo, revisa el diff y haz commit del archivo: es la prueba versionada de "quién vio esto y por qué".
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -q test -Dtest=AuditReportGoldenTests -Dupdate.golden=true -Dsurefire.failIfNoSpecifiedTests=false
echo "Reporte escrito en docs/audit/who-read-report.json"
