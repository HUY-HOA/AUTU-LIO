PRAGMA foreign_keys = ON;

-- Existing non-owner development accounts become ordinary customers.
UPDATE users
SET role = 'CUSTOMER', updated_at = unixepoch()
WHERE role NOT IN ('OWNER', 'CUSTOMER');

-- Remove collaboration, invitation, and review data structures.
DROP TABLE IF EXISTS change_submissions;
DROP TABLE IF EXISTS ui_drafts;
DROP TABLE IF EXISTS script_drafts;
DROP TABLE IF EXISTS project_memberships;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS invitations;
DROP TABLE IF EXISTS permission_overrides;
