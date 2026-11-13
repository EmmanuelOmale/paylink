package com.paylink.banksearch;

import java.util.List;

public record Bank(String bankCode, String displayName, List<String> aliases, long popularity) {}
