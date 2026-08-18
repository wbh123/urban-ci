ALTER TABLE core.inspection_task DROP CONSTRAINT IF EXISTS inspection_task_status_check;
ALTER TABLE core.inspection_task ADD CONSTRAINT inspection_task_status_check
    CHECK (status IN ('PENDING','IN_PROGRESS','ONSITE_COMPLETED','COMPLETED','CANCELLED'));
