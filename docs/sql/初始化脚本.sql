
truncate table agent_node_info;
truncate table firewall_port_info;
truncate table firewall_port_rule;
truncate table firewall_port_rule_info;
truncate table firewall_status_info;


select
    port,
    count(1)
from firewall_port_rule
group by port;