-- Step 3. Creates the two encryption keys INSIDE the database (the values never pass through chat
-- or the repository). Safe to re-run: existing keys are left alone.
do $$
begin
  if not exists(select 1 from vault.secrets where name='aq_pii_key') then
    perform vault.create_secret(encode(extensions.gen_random_bytes(32),'hex'),'aq_pii_key',
      'AES-256 key for customer contact number/address encryption');
  end if;
  if not exists(select 1 from vault.secrets where name='aq_pii_index_key') then
    perform vault.create_secret(encode(extensions.gen_random_bytes(32),'hex'),'aq_pii_index_key',
      'HMAC key for the contact-number blind index');
  end if;
end $$;

-- Step 4. Shows the two keys so the Owner can copy them into a password manager / offline note.
-- If these keys are ever lost, encrypted contacts and addresses CANNOT be recovered.
-- Do not paste them into chat, email, screenshots or the repository.
select name, decrypted_secret from vault.decrypted_secrets where name in ('aq_pii_key','aq_pii_index_key');
