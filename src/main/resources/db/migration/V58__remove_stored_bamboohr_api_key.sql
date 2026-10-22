-- The BambooHR API key moved out of the database into AWS Secrets Manager: the ECS task now passes
-- it to the app as BAMBOOHR_API_KEY (infra/bamboohr.tf), and AppConfigurationService reads it from
-- there, never from app_configuration. Remove the stored copies so the key no longer sits in the
-- database (or its backups going forward) in plain text.
DELETE FROM app_configuration WHERE config_key = 'bamboohr.apiKey';
