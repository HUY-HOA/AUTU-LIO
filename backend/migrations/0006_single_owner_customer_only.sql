PRAGMA foreign_keys = ON;

UPDATE users
SET role = 'CUSTOMER'
WHERE role = 'OWNER';

UPDATE users
SET role = CASE
  WHEN lower(email) = lower('phunghuyhoahaikba@gmail.com') THEN 'OWNER'
  ELSE 'CUSTOMER'
END;

DELETE FROM audit_logs
WHERE action LIKE 'collaborator.%'
   OR action LIKE 'project.%';

DROP TABLE IF EXISTS invitations;
DROP TABLE IF EXISTS permission_overrides;

DROP TABLE IF EXISTS change_submissions;
DROP TABLE IF EXISTS ui_drafts;
DROP TABLE IF EXISTS script_drafts;
DROP TABLE IF EXISTS project_memberships;
DROP TABLE IF EXISTS projects;
