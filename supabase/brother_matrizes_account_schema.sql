-- Brother Matrizes account schema.
-- Applied to Supabase project dwpcddiramxlhavdmmyn on 2026-09-20.
-- Kept here so the backend shape is versioned with the Android app.

create schema if not exists private;

create table if not exists public.brother_matrizes_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '' check (char_length(display_name) <= 80),
  avatar_url text null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.brother_matrizes_subscriptions (
  user_id uuid primary key references auth.users(id) on delete cascade,
  plan_code text not null default 'free' check (char_length(plan_code) between 1 and 40),
  status text not null default 'active'
    check (status in ('active','trialing','past_due','canceled','expired')),
  source text not null default 'brother_matrizes',
  current_period_end timestamptz null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.brother_matrizes_profiles enable row level security;
alter table public.brother_matrizes_subscriptions enable row level security;

create policy "brother_matrizes_profiles_select_own"
on public.brother_matrizes_profiles for select to authenticated
using ((select auth.uid()) = user_id);

create policy "brother_matrizes_profiles_insert_own"
on public.brother_matrizes_profiles for insert to authenticated
with check ((select auth.uid()) = user_id);

create policy "brother_matrizes_profiles_update_own"
on public.brother_matrizes_profiles for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy "brother_matrizes_subscriptions_select_own"
on public.brother_matrizes_subscriptions for select to authenticated
using ((select auth.uid()) = user_id);

grant select, insert, update on public.brother_matrizes_profiles to authenticated;
grant select on public.brother_matrizes_subscriptions to authenticated;
revoke all on public.brother_matrizes_profiles from anon;
revoke all on public.brother_matrizes_subscriptions from anon;

create or replace function private.brother_matrizes_create_default_subscription()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.brother_matrizes_subscriptions(user_id)
  values (new.user_id)
  on conflict (user_id) do nothing;
  return new;
end;
$$;

revoke all on function private.brother_matrizes_create_default_subscription()
from public, anon, authenticated;

create trigger brother_matrizes_profile_default_subscription
after insert on public.brother_matrizes_profiles
for each row
execute function private.brother_matrizes_create_default_subscription();

create or replace function private.brother_matrizes_handle_auth_user_created()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  app_slug text;
  display_name_value text;
begin
  app_slug := coalesce(new.raw_user_meta_data ->> 'app_slug', '');
  if app_slug <> 'brother_matrizes' then
    return new;
  end if;

  display_name_value := left(
    coalesce(
      nullif(new.raw_user_meta_data ->> 'display_name', ''),
      split_part(coalesce(new.email, ''), '@', 1),
      ''
    ),
    80
  );

  insert into public.brother_matrizes_profiles(user_id, display_name)
  values (new.id, display_name_value)
  on conflict (user_id) do nothing;

  return new;
end;
$$;

revoke all on function private.brother_matrizes_handle_auth_user_created()
from public, anon, authenticated;

create trigger brother_matrizes_auth_user_created
after insert on auth.users
for each row
execute function private.brother_matrizes_handle_auth_user_created();


-- Brother Matrizes 0.37.0 subscription catalog and purchase tracking.
-- Prices remain nullable until the checkout is configured. Never trust the
-- Android client to grant or change a paid plan.

create table if not exists public.brother_matrizes_plans (
  code text primary key
    check (char_length(code) between 1 and 40),
  name text not null
    check (char_length(name) between 1 and 80),
  billing_type text not null
    check (billing_type in ('free','monthly','annual','lifetime')),
  is_paid boolean not null default false,
  is_lifetime boolean not null default false,
  is_promotional boolean not null default false,
  description text not null default '',
  price_cents integer null
    check (price_cents is null or price_cents >= 0),
  currency text not null default 'BRL'
    check (char_length(currency) = 3),
  active boolean not null default true,
  available_from timestamptz null,
  available_until timestamptz null,
  display_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

insert into public.brother_matrizes_plans (
  code,
  name,
  billing_type,
  is_paid,
  is_lifetime,
  is_promotional,
  description,
  currency,
  active,
  display_order
)
values
  (
    'free',
    'Gratuito',
    'free',
    false,
    false,
    false,
    'Recursos essenciais para começar no Brother Matrizes.',
    'BRL',
    true,
    0
  ),
  (
    'pro_monthly',
    'Brother Matrizes Pro Mensal',
    'monthly',
    true,
    false,
    false,
    'Acesso Pro com renovação mensal.',
    'BRL',
    true,
    10
  ),
  (
    'pro_annual',
    'Brother Matrizes Pro Anual',
    'annual',
    true,
    false,
    false,
    'Acesso Pro com renovação anual.',
    'BRL',
    true,
    20
  ),
  (
    'lifetime',
    'Brother Matrizes Vitalício',
    'lifetime',
    true,
    true,
    false,
    'Acesso permanente, sem renovação.',
    'BRL',
    true,
    30
  ),
  (
    'lifetime_launch',
    'Brother Matrizes Vitalício • Lançamento',
    'lifetime',
    true,
    true,
    true,
    'Oferta promocional de lançamento com acesso permanente.',
    'BRL',
    true,
    40
  )
on conflict (code) do update
set
  name = excluded.name,
  billing_type = excluded.billing_type,
  is_paid = excluded.is_paid,
  is_lifetime = excluded.is_lifetime,
  is_promotional = excluded.is_promotional,
  description = excluded.description,
  currency = excluded.currency,
  display_order = excluded.display_order,
  updated_at = now();

alter table public.brother_matrizes_subscriptions
  add column if not exists purchased_at timestamptz null,
  add column if not exists purchase_price_cents integer null,
  add column if not exists currency text not null default 'BRL',
  add column if not exists provider text null,
  add column if not exists external_reference text null;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'brother_matrizes_subscriptions_purchase_price_nonnegative'
  ) then
    alter table public.brother_matrizes_subscriptions
      add constraint brother_matrizes_subscriptions_purchase_price_nonnegative
      check (
        purchase_price_cents is null or
        purchase_price_cents >= 0
      );
  end if;

  if not exists (
    select 1
    from pg_constraint
    where conname = 'brother_matrizes_subscriptions_currency_len'
  ) then
    alter table public.brother_matrizes_subscriptions
      add constraint brother_matrizes_subscriptions_currency_len
      check (char_length(currency) = 3);
  end if;
end $$;

update public.brother_matrizes_subscriptions
set plan_code = 'pro_monthly'
where plan_code in ('pro', 'premium');

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'brother_matrizes_subscriptions_plan_code_fkey'
  ) then
    alter table public.brother_matrizes_subscriptions
      add constraint brother_matrizes_subscriptions_plan_code_fkey
      foreign key (plan_code)
      references public.brother_matrizes_plans(code);
  end if;
end $$;

create index if not exists brother_matrizes_subscriptions_plan_status_idx
on public.brother_matrizes_subscriptions(plan_code, status);

create table if not exists public.brother_matrizes_subscription_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null
    references auth.users(id) on delete cascade,
  plan_code text not null
    references public.brother_matrizes_plans(code),
  event_type text not null
    check (
      event_type in (
        'created',
        'activated',
        'renewed',
        'upgraded',
        'downgraded',
        'canceled',
        'expired',
        'payment_failed',
        'refunded',
        'lifetime_purchase'
      )
    ),
  status text not null
    check (
      status in (
        'active',
        'trialing',
        'past_due',
        'canceled',
        'expired'
      )
    ),
  amount_cents integer null
    check (amount_cents is null or amount_cents >= 0),
  currency text not null default 'BRL'
    check (char_length(currency) = 3),
  provider text null,
  external_reference text null,
  occurred_at timestamptz not null default now()
);

create index if not exists brother_matrizes_subscription_history_user_time_idx
on public.brother_matrizes_subscription_history(user_id, occurred_at desc);

create index if not exists brother_matrizes_subscription_history_plan_code_idx
on public.brother_matrizes_subscription_history(plan_code);

alter table public.brother_matrizes_plans enable row level security;
alter table public.brother_matrizes_subscription_history enable row level security;

create policy "brother_matrizes_plans_select_authenticated"
on public.brother_matrizes_plans for select to authenticated
using (true);

create policy "brother_matrizes_subscription_history_select_own"
on public.brother_matrizes_subscription_history for select to authenticated
using ((select auth.uid()) = user_id);

revoke all on public.brother_matrizes_plans from anon;
revoke all on public.brother_matrizes_subscription_history from anon;
revoke all on public.brother_matrizes_plans from authenticated;
revoke all on public.brother_matrizes_subscription_history from authenticated;

grant select on public.brother_matrizes_plans to authenticated;
grant select on public.brother_matrizes_subscription_history to authenticated;

revoke insert, update, delete, truncate, references, trigger
on public.brother_matrizes_subscriptions from authenticated;

grant select on public.brother_matrizes_subscriptions to authenticated;

revoke delete, truncate, references, trigger
on public.brother_matrizes_profiles from authenticated;

grant select, insert, update
on public.brother_matrizes_profiles to authenticated;


-- Brother Matrizes 0.40.0 connected devices and subscription management link.
alter table public.brother_matrizes_subscriptions
  add column if not exists manage_url text null;

create table if not exists public.brother_matrizes_devices (
  user_id uuid not null
    references auth.users(id) on delete cascade,
  device_id text not null
    check (char_length(device_id) between 8 and 128),
  device_name text not null
    check (char_length(device_name) between 1 and 120),
  platform text not null default 'android'
    check (char_length(platform) between 1 and 32),
  app_version text not null default '',
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  primary key (user_id, device_id)
);

create index if not exists brother_matrizes_devices_user_seen_idx
on public.brother_matrizes_devices(user_id, last_seen_at desc);

alter table public.brother_matrizes_devices enable row level security;

create policy "brother_matrizes_devices_select_own"
on public.brother_matrizes_devices for select to authenticated
using ((select auth.uid()) = user_id);

create policy "brother_matrizes_devices_insert_own"
on public.brother_matrizes_devices for insert to authenticated
with check ((select auth.uid()) = user_id);

create policy "brother_matrizes_devices_update_own"
on public.brother_matrizes_devices for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

revoke all on public.brother_matrizes_devices from anon;
revoke all on public.brother_matrizes_devices from authenticated;

grant select, insert, update
on public.brother_matrizes_devices to authenticated;

revoke insert, update, delete, truncate, references, trigger
on public.brother_matrizes_subscriptions from authenticated;

grant select on public.brother_matrizes_subscriptions to authenticated;
