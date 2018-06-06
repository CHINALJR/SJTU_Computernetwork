#!/usr/bin/python

"""
Start up a Simple topology for CS640
"""

from mininet.net import Mininet
from mininet.node import Controller, RemoteController
from mininet.log import setLogLevel, info
from mininet.cli import CLI
from mininet.topo import Topo
from mininet.util import quietRun

import sys
import string

IPCONFIG_FILE = "./ip_config"
hosts = []
routers = []
links = []
nodes = {}
nodeIPs = {}
ifaceIPs = {}

class SimpleRouterTopo(Topo):
    "Simple Router Topology"
    
    def __init__( self, *args, **kwargs ):
        global nodeIPs
        Topo.__init__( self, *args, **kwargs )
        self.graph = Graph()
        for host in hosts:
            nodes[host] = self.addHost(host)
            self.graph.add_node(host)
        for router in routers:
            nodes[router] = self.addSwitch(router)
            self.graph.add_node(router)
        for link in links:
            nameA = link[0]
            nameB = link[1]
            nodeA = nodes[nameA]
            nodeB = nodes[nameB]
            self.addLink(nameA, nameB)
            if nameA not in nodeIPs:
                nodeIPs[nameA] = []
            nodeIPs[nameA].append([link[2], link[3]])
            if nameB not in nodeIPs:
                nodeIPs[nameB] = []
            nodeIPs[nameB].append([link[3], link[2]])
            self.graph.add_edge(nameA, nameB, 1)


def startsshd(host):
    "Start sshd on host"
    stopsshd()
    info( '*** Starting sshd\n' )
    name, intf, ip = host.name, host.defaultIntf(), host.IP()
    banner = '/tmp/%s.banner' % name
    host.cmd( 'echo "Welcome to %s at %s" >  %s' % ( name, ip, banner ) )
    host.cmd( '/usr/sbin/sshd -o "Banner %s"' % banner, '-o "UseDNS no"' )
    info( '***', host.name, 'is running sshd on', intf, 'at', ip, '\n' )


def stopsshd():
    "Stop *all* sshd processes with a custom banner"
    info( '*** Shutting down stale sshd/Banner processes ',
          quietRun( "pkill -9 -f Banner" ), '\n' )


def starthttp(host):
    "Start simple Python web server on hosts"
    info( '*** Starting SimpleHTTPServer on host', host, '\n' )
    host.cmd( 'cd ./http_%s/; nohup python2.7 ./webserver.py &' % (host.name) )


def stophttp():
    "Stop simple Python web servers"
    info( '*** Shutting down stale SimpleHTTPServers', 
          quietRun( "pkill -9 -f SimpleHTTPServer" ), '\n' )    
    info( '*** Shutting down stale webservers', 
          quietRun( "pkill -9 -f webserver.py" ), '\n' )    
   

def configure_host(node, hostIPs):
    info( '*** Configuring host %s\n' % node)
    count = 0
    for intf in node.intfList():
        hostip = hostIPs[count][0]
        routerip = hostIPs[count][1]
        netip = hostip.split('.')[:3]
        netip.append('0')
        netip = string.join(netip, '.')
        info('Setting ip for %s to %s/24\n' % (intf, hostip))
        intf.setIP('%s/24' % hostip)
        info('Adding route %s/32 dev %s\n' % (routerip, intf))
        node.cmd('route add %s/32 dev %s' % (routerip, intf))
        info('Deleting route %s/24 dev %s\n' % (netip, intf))
        node.cmd('route del -net %s/24 dev %s' % (netip, intf))
        if (0 == count):
            info('Adding default gw %s dev %s\n' % (routerip, intf))
            node.cmd('route add default gw %s dev %s' % (routerip, intf))
        count = count + 1

def load_topofile(topofile):
    global hosts
    global routers
    global links
    info( '*** Loading topology file %s\n' % topofile)
    try:
        with open(topofile, 'r') as f:
            count = 0
            for line in f:
                if (0 == count):
                    hosts = line.split()                
                    count = count + 1
                    info( 'Hosts %s\n' % hosts)
                elif (1 == count):
                    routers = line.split()
                    count = count + 1
                    info( 'Routers %s\n' % routers)
                else:
                    if (len(line.split()) != 4):
                        break
                    link = line.split()
                    links.append(link)
                    info( 'Link %s\n' % link)
            f.close()
    except EnvironmentError:
        sys.exit("Couldn't load topology file, check whether %s exists" % topofile)


def write_ipfile(ipfile):
    info( '*** Writing IP file %s\n' % ipfile)
    try:
        with open(ipfile, 'w') as f:
            for host in hosts:
                count = 0
                for ip in nodeIPs[host]:
                    iface = '%s-eth%d' % (host, count)
                    f.write('%s %s %s\n' % (iface, ip[0], '255.255.255.0'))
                    ifaceIPs[iface] = ip[0]
                    count = count + 1
            for router in routers:
                count = 1
                for ip in nodeIPs[router]:
                    iface = '%s-eth%d' % (router, count)
                    f.write('%s %s %s\n' % (iface, ip[0], '255.255.255.0'))
                    ifaceIPs[iface] = ip[0]
                    count = count + 1
            f.close()
    except EnvironmentError:
        sys.exit("Couldn't write IP file" % ipfile)


def write_rtablefile(router, graph):
    rtablefile = "rtable.%s" % router
    info( '*** Writing rtable file %s\n' % rtablefile)
    visited, path = dijkstra(graph, router)
    try:
        with open(rtablefile, 'w') as f:
            for host in hosts:
                via = path[host]
                hostip = ifaceIPs['%s-eth0' % host]
                if (via == router):
                    count = 1
                    for ip in nodeIPs[router]:
                        if hostip == ip[1]:
                            break
                        count = count + 1
                    f.write("%s %s 255.255.255.255 eth%d\n" % (hostip, '0.0.0.0', count))
                else:
                    routerip = ''
                    gwip = ''
                    for link in links:
                        if (link[0] == router and link[1] == via):
                            routerip = link[2]
                            gwip = link[3]
                        elif (link[0] == via and link[1] == router):
                            routerip = link[3]
                            gwip = link[2]
                    count = 1
                    for ip in nodeIPs[router]:
                        if routerip == ip[0]:
                            break
                        count = count + 1
                    f.write("%s %s 255.255.255.255 eth%d\n" % (hostip, gwip, count))
            f.close()
    except EnvironmentError:
        sys.exit("Couldn't write IP file" % ipfile)
    

#############################################################################
# Implementation of Dijkstra's Algorithm from Lynn Root
# https://gist.github.com/econchick/4666413
class Graph:
  def __init__(self):
    self.nodes = set()
    self.edges = {}
    self.distances = {}

  def add_node(self, value):
    self.nodes.add(value)
    self.edges[value] = []

  def add_edge(self, from_node, to_node, distance):
    self.edges[from_node].append(to_node)
    self.edges[to_node].append(from_node)
    self.distances[(from_node, to_node)] = distance
    self.distances[(to_node, from_node)] = distance


def dijkstra(graph, initial):
  visited = {initial: 0}
  path = {}

  nodes = set(graph.nodes)

  while nodes: 
    min_node = None
    for node in nodes:
      if node in visited:
        if min_node is None:
          min_node = node
        elif visited[node] < visited[min_node]:
          min_node = node

    if min_node is None:
      break

    nodes.remove(min_node)
    current_weight = visited[min_node]

    for edge in graph.edges[min_node]:
      weight = current_weight + graph.distances[(min_node, edge)]
      if edge not in visited or weight < visited[edge]:
        visited[edge] = weight
        path[edge] = min_node

  return visited, path
#############################################################################

def simple_router_net():
    "Create a network for use with the simple router"
    topo = SimpleRouterTopo()
    write_ipfile(IPCONFIG_FILE)
    for router in routers:
        write_rtablefile(router, topo.graph)

    info( '*** Creating network\n' )
    net = Mininet( topo=topo, controller=RemoteController, autoSetMacs=True )
    net.start()

    for host in hosts:
        node = net.get(host)
        configure_host(node, nodeIPs[host])
        starthttp(node)
    CLI( net )
    stophttp()
    net.stop()

if __name__ == '__main__':
    setLogLevel( 'info' )
    if (len(sys.argv) != 2):
        print sys.argv
        sys.exit("%s <topofile>" % (sys.argv[0]))
    topofile = sys.argv[1]
    load_topofile(topofile)
    simple_router_net()
