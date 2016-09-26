/*
 * NetBurst: A Java-based RDMA network performance benchmark
 *
 * Author: Animesh Trivedi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.animeshtrivedi;

import org.apache.commons.cli.*;

/**
 * Created by atr on 9/26/16.
 */
/*
Netperf configurations
-p (int)        RDMA port number, default is 20886.
-m (int,int)    TX, RX buffer sizes. For all operations -m TX_size is the
                effective I/O tranfer size. For -m TX_size, RX_size, RX_size is
                only used for SEND_BW/LAT tests.
                Allowed suffixes: {K,M,G} are for base 2, and {k,m,g} for base 10).
-M (int)        The size of the peer IO area (can be flash backed). This is the
                target area for one-sided operations. You may want to use -c with
                this to cover all possible area. Must be greater than or equal to -m.
                Unique remote targets would be -M/-m entries.
                Allowed suffixes: {K,M,G} are for base 2, and {k,m,g} for base 10).
-c              It will try to cover all peer I/O area, and will dynamically
                change the remote target for every operation. Respects -R.
-P (int,int)    Poll or block (local, remote) for a completion notification.
                1=poll, 0=block
-n (int,int)    Number of unique client-side (TX, RX) buffers. These buffers will be
                used to populate -i TX/RX WQEs. This must be >= 1. Default bw=8
-N (int,int)    Same as -n but for the peer (server) side.
-L              Use large/huge page for buffer allocation. In case the buffer size
                is less than the page size, the allocation size will be rounded up.
                This code is tested for x86_64 for 2MB and 1GB sizes.
-z              Use mmap instead of posix_memalign. -L is only valid with -z
-s (int)        Switch on/off signaled posting, default is on. 1=on, 0=off.
                N/A for latency tests.
-d (str)        Name of rNIC device on which memory registration latency test
                is done. Device name is what you see in ibv_devices command.
-i (int)        The size of send queue, the default is 8. The i elements will
                be populated by using -n/N unique buffers. N/A for latency tests.
                We don't do pipelined latency.
-I (int, int)   How many storage offset addresses we want? (local = (one-sided r/w), remote=(valid for send/recv)).
-o (int,int)    Flash file buffer offset (local, remote) from where to do the
                test for -m bytes -a flash file path to be used in the flash test
                Allowed suffixes: {K,M,G} are for base 2, and {k,m,g} for base 10).
-a              HS4 iomem file path. This will be sent to server for remote HS4 tests.
-A              Indicates that -a str is a this is a local HS4 test.
-B              Indicates that -a str is a flashnet file on the netserver.
-C              Indicates that -a str is a normal file on the netserver.
-R              If set then will do random I/O.
-t (int,int)    Touch TX, RX buffers before the test starts. The touching will
                bring them into the CPU caches.
                0 = no, 1 = read (clean cache), 2 = write (dirty cache).
-T (int,int)    Same as -t but for remote side.
-u              Perform the test with UDP/SIW (needs siw2 with UDP)
-H (str)        The "IP" where to do the UDP test.
-X              Netperf makes you a cup of coffee! ;)
-v              Verbose, will display the current configuration used.
-h              To show this message.

For detailed debugging enable RDMA_DEBUG in nettest_rdma.h. Because RDMA
interconnect latencies are very low, we don't do run-time debugging.

Comments and bugs: Animesh Trivedi <atr@zurich.ibm.com>
*/

public class NetBurstConfig {
    private Options options;
    String[] hostNames;
    String[] testNames = {"read", "write"};
    int testNameIndex = 0;
    String[] topology = {"allToAll", "pairs"};
    int topologyIndex = 0;
    int instances = 1;
    int messageSize = 4096;
    int regionSize = 4096;
    int inFlight = 8;
    boolean poll = false;
    int portMaster = 20208;
    int portSlave = 20209;


    static public String expandStringArray(String[] stringArray){
        if(stringArray == null)
            return "null";

        String result = "{ ";
        for(int i = 0; i < stringArray.length; i++){
            result+=stringArray[i];
            if(i!=stringArray.length - 1)
                result+=", ";
        }
        result+=" }";
        return result;
    }

    static public int getMatchingIndex(String[] options, String name) {
        int i;
        for(i = 0; i < options.length; i++)
            if (name.equalsIgnoreCase(options[i])){
                return i;
            }
        throw  new IllegalArgumentException(name + " not found in " + NetBurstConfig.expandStringArray(options));
    }

    static public int sizeStrToBytes(String str) {
        String lower = str.toLowerCase();
        int val;
        if (lower.endsWith("k")) {
            val = Integer.parseInt(lower.substring(0, lower.length() - 1)) * 1024;
        } else if (lower.endsWith("m")) {
            val = Integer.parseInt(lower.substring(0, lower.length() - 1)) * 1024 * 1024;
        } else if (lower.endsWith("g")) {
            val = Integer.parseInt(lower.substring(0, lower.length() - 1)) * 1024 * 1024 * 1024;
        } else {
            // no suffix, so it's just a number in bytes
            val = Integer.parseInt(lower);
        }
        return val;
    }


    public NetBurstConfig(){
        this.options = new Options();
        this.options.addOption("n","testName", true, "Name of the test, valid entries are: read, write");
        this.options.addOption("H", "hostNames", true, "List of comma separated hostnames");
        this.options.addOption("T", "topology", true, "Topology of the test, allToAll or Pairs");
        this.options.addOption("I", "instances", true, "Number of parallel instances of the test");
        this.options.addOption("m", "mSize", true, "Message size");
        this.options.addOption("M", "regionSize", true, "Region size");
        this.options.addOption("i", "inFlight", true, "Max in flight");
        this.options.addOption("P", "poll", true,"Poll or not");
        this.options.addOption("p","port", true, "<int,int> Starting port number, master,slaves");
    }

    public void showOptions(){
        String status = "\n";
        status+="\n testName      : " + this.testNames[this.testNameIndex];
        status+="\n hostNames     : " + expandStringArray(this.hostNames);
        status+="\n Topology      : " + this.topology[this.topologyIndex];
        status+="\n Instances     : " + this.instances;
        status+="\n Message size  : " + this.messageSize;
        status+="\n Region size   : " + this.regionSize;
        status+="\n Inflight      : " + this.inFlight;
        status+="\n Poll          : " + this.poll;
        status+="\n Port          : " + this.portMaster + "," + this.portSlave;
        System.out.println(status);
    }

    public void show_help() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Main", options);
    }

    public void parse(String[] args) {
        CommandLineParser parser =  new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                show_help();
                System.exit(0);
            }
            if (cmd.hasOption("n")) {
                this.testNameIndex = getMatchingIndex(this.testNames,
                        cmd.getOptionValue("n").trim());
            }
            if (cmd.hasOption("H")) {
                /* here we have to parse comma separated hostnames */
                this.hostNames = cmd.getOptionValue("H").trim().split(",");
                for(int i = 0; i < this.hostNames.length; i++) {
                    this.hostNames[i] = this.hostNames[i].trim();
                }
            }
            if (cmd.hasOption("T")) {
                this.topologyIndex = getMatchingIndex(this.topology,
                        cmd.getOptionValue("T").trim());
            }
            if (cmd.hasOption("N")) {
                this.instances = Integer.parseInt((cmd.getOptionValue("N")));
            }
            if (cmd.hasOption("m")) {
                this.messageSize = sizeStrToBytes(cmd.getOptionValue("m"));
            }
            if (cmd.hasOption("M")) {
                this.regionSize = sizeStrToBytes(cmd.getOptionValue("M"));
            }
            if(cmd.hasOption("i")){
                this.inFlight = sizeStrToBytes(cmd.getOptionValue("i"));
            }
            if(cmd.hasOption("P")){
                this.poll = sizeStrToBytes(cmd.getOptionValue("P")) != 0;
            }
            if(cmd.hasOption("p")){
                String[] port = cmd.getOptionValue("P").trim().split(",");
                if(port.length != 2){
                    System.err.println("Something is wrong ");
                    show_help();
                    System.exit(-1);
                }
                if(!port[0].isEmpty())
                    this.portMaster = Integer.parseInt(port[0]);
                if(!port[1].isEmpty())
                    this.portSlave = Integer.parseInt(port[1]);
            }
        } catch (ParseException e) {
            System.err.println("Failed to parse command line properties" + e);
            show_help();
            System.exit(-1);
        }
        showOptions();
    }
}
