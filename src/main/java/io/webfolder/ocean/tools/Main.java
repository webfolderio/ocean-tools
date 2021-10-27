package io.webfolder.ocean.tools;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.myjeeva.digitalocean.DigitalOcean;
import com.myjeeva.digitalocean.impl.DigitalOceanClient;
import com.myjeeva.digitalocean.pojo.Droplet;
import com.myjeeva.digitalocean.pojo.Droplets;
import com.myjeeva.digitalocean.pojo.Firewall;
import com.myjeeva.digitalocean.pojo.Firewalls;
import com.myjeeva.digitalocean.pojo.InboundRules;
import com.myjeeva.digitalocean.pojo.Sources;

public class Main {

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.err.println("Missing DO auth token!. Usage: <auth token> <command> (add or remove) <droplet name>");
			System.exit(-1);
			return;
		}
		String token = args[0];
		boolean isAdd = "add".equals(args[1].trim());
		boolean isRemove = "remove".equals(args[1].trim());

		if (!isAdd && !isRemove) {
			System.err.println("Missing command. Please specify the command name (add or remove).");
			System.exit(-2);
			return;
		}

		String dropletName = args[2].trim();

		CloseableHttpClient httpClient = HttpClients.createDefault();

		DigitalOcean apiClient = new DigitalOceanClient("v2", token, httpClient);
		Droplets droplets = apiClient.getAvailableDroplets(0, 10);
		List<Droplet> dropletList = droplets.getDroplets();
		Droplet found = null;
		for (Droplet next : dropletList) {
			if (next.getName().equals(dropletName)) {
				found = next;
				break;
			}
		}
		Firewalls firewalls = apiClient.getAvailableFirewalls(0, 10);
		List<Firewall> firewallList = firewalls.getFirewalls();
		Firewall foundFirewall = null;
		for (Firewall next : firewallList) {
			if (next.getDropletIds().contains(found.getId())) {
				foundFirewall = next;
				break;
			}
		}

		CloseableHttpResponse httpResponse = httpClient.execute(new HttpGet("https://icanhazip.com/"));
		String externalIpAddress = EntityUtils.toString(httpResponse.getEntity()).trim();
		httpResponse.close();

		System.out.println("External IP Address: " + externalIpAddress);

		if (externalIpAddress.isBlank()) {
			System.err.println("Can't detect ip address!");
			System.exit(-1);
			return;
		}

	    for (InboundRules rules : foundFirewall.getInboundRules()) {
	    	Sources nextSource = rules.getSources();
	    	List<String> copy = new ArrayList<>(nextSource.getAddresses());
	    	if (nextSource != null) {
	    		if (nextSource.getAddresses().contains(externalIpAddress)) {
	    			if (isAdd) {
	    				System.err.println("IP Address " + externalIpAddress + " has already permission");
	    				System.exit(-2);
	    			} else if (isRemove) {
	    				copy.remove(externalIpAddress);
	    			}
	    		}
	    	}
	    	if (nextSource.getAddresses().size() != copy.size()) {
	    		if (copy.isEmpty()) {
	    			nextSource.setAddresses(null);
	    		} else {
	    			nextSource.setAddresses(copy);
	    		}
	    	}
	    }

	    if (isAdd) {
	    	InboundRules inboundRules = new InboundRules();
	    	inboundRules.setProtocol("tcp");
	    	inboundRules.setPorts("22");

	    	Sources sources = new Sources();
	    	sources.setAddresses(asList(externalIpAddress));
	    	inboundRules.setSources(sources);

	    	foundFirewall.getInboundRules().add(inboundRules);
	    } else if (isRemove) {
	    	List<InboundRules> emptyInboundRules = new ArrayList<>();
	    	for (InboundRules next : foundFirewall.getInboundRules()) {
	    		if (next.getSources() != null) {
	    			boolean isDropletIdsEmpty = next.getSources().getDropletIds() == null || next.getSources().getDropletIds().isEmpty();
	    			boolean isAddressEmpty = next.getSources().getAddresses() == null || next.getSources().getAddresses().isEmpty();
	    			if (isDropletIdsEmpty && isAddressEmpty) {
	    				emptyInboundRules.add(next);
	    			}
	    		}
	    	}
	    	foundFirewall.getInboundRules().removeAll(emptyInboundRules);
	    }

		apiClient.updateFirewall(foundFirewall);
	}
}
