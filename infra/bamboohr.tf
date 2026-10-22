# ---------- BambooHR API key ----------
#
# The key used to live in the app's database (app_configuration, entered on the Configuration page
# and shown there in plain text). It now lives here, and the ECS task passes it to the container as
# BAMBOOHR_API_KEY (ecs.tf), which Spring binds to bamboohr.api-key.
#
# Terraform owns the secret but NOT its value: there is deliberately no aws_secretsmanager_secret_version,
# so the key never enters Terraform state or this repository. Set or rotate it out of band, then
# redeploy the app so a new task reads it:
#
#   aws secretsmanager put-secret-value --secret-id wfm-service/dev/bamboohr-api-key \
#       --secret-string file://<file holding only the key>
resource "aws_secretsmanager_secret" "bamboohr_api_key" {
  name                    = "${var.app_name}/${var.environment}/bamboohr-api-key"
  description             = "BambooHR API key for the WFM service (value set out of band, not by Terraform)"
  recovery_window_in_days = 7
}
