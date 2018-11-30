require 'json'
require 'open-uri'

def get_as_json(url)
  raw = URI.parse(url).read
  JSON.parse(raw)
end

current_stable_train = nil
current_dev_train = nil

current_stable_version = nil
current_dev_version = nil

metadata = get_as_json("https://api.github.com/repos/killbill/killbill/tags")
releases = []
metadata.each do |entry|
  parsed = entry['name'].scan(/killbill-([0-9]+\.([0-9]+)\.[0-9]+)/).last
  version = parsed.first

  train = parsed.last.to_i
  if train % 2 == 1
    current_dev_train = train if current_dev_train.to_i < train
    current_dev_version = version if current_dev_version.nil? || (Gem::Version.new(current_dev_version) < Gem::Version.new(version))
  else
    current_stable_train = train if current_stable_train.to_i < train
    current_stable_version = version if current_stable_version.nil? || (Gem::Version.new(current_stable_version) < Gem::Version.new(version))
  end

  releases << {
    :train => train,
    :version => version
  }
end

doc =<<EOF
## Top level keys
# general.notice = This notice should rarely, if ever, be used as everyone will see it

EOF

current_train = nil
latest_from_train = nil
releases.each do |release|
  if release[:train] != current_train || current_train == nil
    current_train = release[:train]
    latest_from_train = release[:version]
    doc << "### 0.#{current_train}.x series ###\n\n"
  end

  doc << "\# #{release[:version]}\n"

  if release[:version] == latest_from_train
    doc << "#{release[:version]}.updates           =\n"
  else
    doc << "#{release[:version]}.updates           = #{latest_from_train}\n"
  end

  if release[:version] == current_dev_version || release[:version] == current_stable_version
    doc << "#{release[:version]}.notices           = This is the latest #{release[:train] % 2 == 1 ? 'dev' : 'GA'} release.\n"
  elsif release[:train] != current_dev_train
    doc << "#{release[:version]}.notices           = We recommend upgrading to #{current_stable_version}, our latest GA release.\n"
  else
    doc << "#{release[:version]}.notices           = We recommend upgrading to #{current_dev_version}, our latest dev release.\n"
  end

  doc << "#{release[:version]}.release-notes     = https://github.com/killbill/killbill/releases/tag/killbill-#{release[:version]}\n\n"
end

puts doc.chomp!