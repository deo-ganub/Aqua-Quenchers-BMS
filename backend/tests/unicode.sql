\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()))::text,true);
set local role authenticated;
do $$declare p jsonb;r jsonb;denied boolean:=false;begin
 if current_setting('server_encoding')<>'UTF8' then raise exception 'Unicode test requires UTF8 database';end if;
 p:=jsonb_build_object('operation','create_expense','request_id',gen_random_uuid(),'business_date','2026-10-15','category','Others','amount_centavos',1,'remarks',repeat('😀',500),'selection_source','manual');
 r:=public.aq_command(p);
 if char_length(r->>'remarks')<>500 then raise exception 'Unicode code point count mismatch';end if;
 begin perform public.aq_command(p||jsonb_build_object('request_id',gen_random_uuid(),'remarks',repeat('😀',501)));exception when others then denied:=true;end;
 if not denied then raise exception '501 code point expense accepted';end if;
 raise notice 'PASS: UTF8 expense 500/501 code point boundary';
end $$;
rollback;
