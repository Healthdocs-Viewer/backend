INSERT INTO auth_identity (id, user_id, provider, issuer, subject)
SELECT gen_random_uuid(), id, 'local', '${auth-issuer}', external_subject
FROM app_user
ON CONFLICT (provider, issuer, subject) DO NOTHING;
