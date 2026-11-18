-- NEXORA VPN Initial Seed Data (GCC WireGuard Topology)
-- IMPORTANT: All GCC server slots are initialized as 'pending_vps' with NULL public_ip,
-- endpoint, and server_public_key until real Linux VPS hardware is provisioned via
-- backend/scripts/setup-wg-vps.sh and verified online. Never invent server IPs or keys.

INSERT INTO vpn_servers (
  node_code,
  name,
  country_name,
  country_code,
  city,
  server_port,
  ipv4_subnet,
  ipv6_subnet,
  tier,
  health_status
)
VALUES
  ('SA01', 'Riyadh SA01', 'Saudi Arabia', 'SA', 'Riyadh', 51820, '10.8.1.0/24', 'fd80:4e58:5201::/64', 'free', 'pending_vps'),
  ('SA02', 'Jeddah SA02', 'Saudi Arabia', 'SA', 'Jeddah', 51820, '10.8.2.0/24', 'fd80:4e58:5202::/64', 'pro', 'pending_vps'),
  ('AE01', 'Dubai AE01', 'United Arab Emirates', 'AE', 'Dubai', 51820, '10.8.3.0/24', 'fd80:4e58:5203::/64', 'free', 'pending_vps'),
  ('AE02', 'Abu Dhabi AE02', 'United Arab Emirates', 'AE', 'Abu Dhabi', 51820, '10.8.4.0/24', 'fd80:4e58:5204::/64', 'pro', 'pending_vps'),
  ('BH01', 'Manama BH01', 'Bahrain', 'BH', 'Manama', 51820, '10.8.5.0/24', 'fd80:4e58:5205::/64', 'free', 'pending_vps'),
  ('QA01', 'Doha QA01', 'Qatar', 'QA', 'Doha', 51820, '10.8.6.0/24', 'fd80:4e58:5206::/64', 'pro', 'pending_vps'),
  ('KW01', 'Kuwait City KW01', 'Kuwait', 'KW', 'Kuwait City', 51820, '10.8.7.0/24', 'fd80:4e58:5207::/64', 'pro', 'pending_vps'),
  ('OM01', 'Muscat OM01', 'Oman', 'OM', 'Muscat', 51820, '10.8.8.0/24', 'fd80:4e58:5208::/64', 'free', 'pending_vps')
ON CONFLICT (node_code) DO NOTHING;
