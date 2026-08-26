DELETE FROM aecp_organization_member
WHERE organization_id IN ('ORG-DEMO-COMAC', 'ORG-DEMO-AECC')
   OR user_id IN (
       'USR-DEMO-PM',
       'USR-DEMO-ADMIN-A',
       'USR-DEMO-ADMIN-B',
       'USR-DEMO-ENG-A',
       'USR-DEMO-ENG-B',
       'USR-DEMO-AUDITOR'
   );

DELETE FROM aecp_user_account
WHERE id IN (
    'USR-DEMO-PM',
    'USR-DEMO-ADMIN-A',
    'USR-DEMO-ADMIN-B',
    'USR-DEMO-ENG-A',
    'USR-DEMO-ENG-B',
    'USR-DEMO-AUDITOR'
);

DELETE FROM aecp_organization
WHERE id IN ('ORG-DEMO-COMAC', 'ORG-DEMO-AECC');
