#! /usr/bin/env ruby

require 'aws-sdk'
require 'net/http'
require 'net/scp'
require 'net/ssh'
require 'openssl'
require 'tmpdir'
require 'yaml'

SHH_PRIVATE_KEY = ""

class KillBillConfig
  attr_reader :config

  def initialize(path)
    @config = YAML.load(File.open(path))
    @working_dir = Dir.mktmpdir
    puts "Working directory is #{@working_dir}"

    @config_dir = Dir.mkdir(File.join(@working_dir, 'config'))
    @binaries_dir = Dir.mkdir(File.join(@working_dir, 'binaries'))
  end

  def cleanup
    FileUtils.remove_entry @working_dir
  end

  def download_world
    download_killbill
    download_plugins
  end

  private

  def download_killbill
    version = (@config[:killbill] || {})[:version]

    # Download the binary
    download('com.ning.billing', 'killbill-server', version, 'war', 'jar-with-dependencies')

    # Copy the configuration
    config (@config[:killbill] || {})[:config]
    FileUtils.cp config, @config_dir if config
  end

  def download_plugins
    plugins = (@config[:plugins] || [])
    plugins.each do |plugin_name, plugin_def|
      version = plugin_def[:version]

      # Download the binary
      download (plugin_def[:group_id] || 'com.ning.killbill.ruby'),
               (plugin_def[:artifact_id] || plugin_name),
               version

      # Copy the configuration
      FileUtils.cp plugin_def[:config], @config_dir if plugin_def[:config]
    end
  end

  def download(group_id, artifact_id, version=nil, packaging='tar.gz', classifier=nil)
    fetch("https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=#{group_id}&a=#{artifact_id}&c=#{classifier}&p=#{packaging}&v=#{version||'LATEST'}",
          "#{artifact_id}#{'-' + version if version}.#{packaging}")
  end

  def fetch(uri_string, filename, limit=10)
    return if limit <= 0

    uri = URI(uri_string)
    Net::HTTP.start(uri.host, uri.port, {:use_ssl => true, :verify_mode => OpenSSL::SSL::VERIFY_NONE}) do |http|
      request = Net::HTTP::Get.new uri.request_uri

      http.request request do |response|
        case response
          when Net::HTTPSuccess then
            puts "Downloading #{uri.request_uri}"
            open "#{@binaries_dir}/#{filename}", 'w' do |io|
              response.read_body do |chunk|
                io.write chunk
              end
            end
          when Net::HTTPRedirection then
            #puts "Redirect from #{uri.request_uri} to #{response['location']}"
            fetch(response['location'], filename, limit - 1)
          else
            response.error!
        end
      end
    end
  end
end

class SimpleEC2

  attr_reader :aws_ec2, :ami, :instance_type, :zone, :key_name, :security_groups
  
  def initialize(access_key, secret_key, ami, instance_type, zone, key_name, security_groups)
    @aws_ec2 =  AWS::EC2.new({:access_key_id => access_key,
                  :secret_access_key => secret_key })  
    @ami = ami
    @instance_type = instance_type
    @zone = zone
    @key_name = key_name   
    @security_groups = security_groups     
  end
  
  def create_instance(wait_for_completion)
    result = @aws_ec2.instances.create(:image_id => @ami,
                         :security_groups => @security_groups,
                         :instance_type => @instance_type,
                         :placement => { :availability_zone => @zone},
                         :key_name => @key_name)
     add_tags(result.id, [{:key => 'role',
                          :value => 'ri-test'}])
     wait_for_instance(result.id, :running) if wait_for_completion
     result.id
  end

  def terminate_all_running_instances(wait_for_completion)
    
    instances = running_instances
    return if instances.length == 0
    terminate_instances(instances.map do |i|
      i[:id]
    end, wait_for_completion)
  end
  
  def show_running_instances
    instances = running_instances
    return if instances.length == 0
    instances.each do |r|
      puts "instance = #{r.inspect}"
    end
  end

  def stop_instances(instances_id, force, wait_for_completion)
    result = @aws_ec2.client.stop_instances({:instance_ids => instances_id},
                               :force => force)
    result.instances_set.each do |r|
      wait_for_instance(r.id, :stopped) if wait_for_completion
      puts "Instance #{r.instance_id} is now #{r.current_state.name}"                        
    end                           
  end

  def terminate_instances(instances_id, wait_for_completion)
    
    result = @aws_ec2.client.terminate_instances({:instance_ids => instances_id})
    result.instances_set.each do |r|
      wait_for_instance(r.instance_id, :terminated) if wait_for_completion
      puts "Instance #{r.instance_id} is now #{r.current_state.name}"                        
    end                           
  end

  def upload_file_to_running_instances(src, dest)
    running_instances.each do |i|
      upload_file(i[:dns], src, dest)
    end
  end

  def upload_file(dns_name, src, dest)
    puts "Uploading file #{src} to #{dns_name}:#{dest}"
    Net::SCP.start(dns_name, "ubuntu", :keys => [SHH_PRIVATE_KEY] ) do |scp|
       scp.upload! src, dest
    end
  end
  
  def run_remote_script(dns_name, script)
    puts "Starting script #{script} on #{dns_name}"
    Net::SSH.start(dns_name, "ubuntu", :keys => [SHH_PRIVATE_KEY] ) do |ssh|
      result = ssh.exec!(script)
      puts "result = #{result}"
    end
  end
  
  private
  
  def wait_for_instance(instance_id, state)
    puts "wait_for_instance #{instance_id} => state = #{state}"
    has_completed = false
    begin
      instance = @aws_ec2.instances[instance_id]
      if instance.nil?
        puts "Null instance"
        return
      end

      current_status = instance.status
      has_completed = case state
        when :terminated then instance.nil? || current_status == state
        else current_status == state
      end
      puts "Waiting for instance = #{instance_id} #{current_status} -> #{state} " if !has_completed
      sleep(1.0)
    end while !has_completed
  end
  
  def running_instances
    instances = @aws_ec2.instances
    running_instances = []
    instances.each do |i|
      running_instances.push(i) if i.status == :running
    end

    running_instances.map do |r| 
       {:id => r.id,
        :dns => r.dns_name,
        :status => r.status,
        :zone => r.availability_zone,
        :key_pair => r.key_pair.name,
        :image => r.image.id,
        :security_groups => r.security_groups.to_ary.map do |sr|
          sr.name
        end.join(",")}
     end
  end
  
  def add_tags(instance_id, tags)
    @aws_ec2.client.create_tags(:resources => [instance_id],
                           :tags => tags)
  end
  
end


