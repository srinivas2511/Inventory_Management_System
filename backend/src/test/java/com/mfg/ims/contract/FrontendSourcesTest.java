package com.mfg.ims.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrontendSourcesTest {

    private static FrontendSources scan(String file, String ts, String apiBase) {
        FrontendSources fs = new FrontendSources();
        fs.scanText(file, ts, apiBase);
        return fs;
    }

    @Test
    void resolvesAFilesLiteralBaseAndTemplateParameters() {
        FrontendSources fs = scan("crm.api.ts", """
                export class CrmApi {
                  private base = '/api/crm';

                  get(id: number) {
                    return this.http.get<Page<LeadDto>>(`${this.base}/leads/${id}`, { params });
                  }
                }
                """, "");
        assertThat(fs.calls).hasSize(1);
        FrontendSources.Call c = fs.calls.get(0);
        assertThat(c.verb()).isEqualTo("GET");
        assertThat(c.url()).isEqualTo("/api/crm/leads/{}");
        assertThat(c.generic()).isEqualTo("Page<LeadDto>");
        assertThat(c.resolved()).isTrue();
    }

    @Test
    void anApiBaseUrlOfSlashApiDoublesThePrefixOfPathsThatAlreadyStartWithApi() {
        String ts = """
                export class WarehouseApi {
                  private readonly base = inject(AppConfigService).apiBaseUrl;

                  list() {
                    return this.http.get<WarehouseDto[]>(`${this.base}/api/warehouses`);
                  }
                }
                """;
        assertThat(scan("w.ts", ts, "/api").calls.get(0).url()).isEqualTo("/api/api/warehouses");
        assertThat(scan("w.ts", ts, "").calls.get(0).url()).isEqualTo("/api/warehouses");
    }

    @Test
    void callsThroughApiServicePrependTheConfiguredBase() {
        String ts = "class A {\n  go() {\n    return this.api.get<X>('/api/x/ping');\n  }\n}\n";
        assertThat(scan("a.ts", ts, "/api").calls.get(0).url()).isEqualTo("/api/api/x/ping");
        assertThat(scan("a.ts", ts, "").calls.get(0).url()).isEqualTo("/api/x/ping");
    }

    @Test
    void ignoresQueryStringsAndFlagsDynamicSuffixes() {
        FrontendSources fs = scan("q.ts", """
                class Q {
                  a(code: string, q: string) {
                    return this.http.get(`/api/reports/${code}?q=${q}`);
                  }
                  b(suffix: string) {
                    return this.http.get(`/api/foo${suffix}`);
                  }
                }
                """, "");
        assertThat(fs.calls.get(0).url()).isEqualTo("/api/reports/{}");
        assertThat(fs.calls.get(0).dynamicSuffix()).isFalse();
        assertThat(fs.calls.get(1).dynamicSuffix()).isTrue();
        assertThat(fs.calls.get(0).methodKey()).isNotEqualTo(fs.calls.get(1).methodKey());
    }

    @Test
    void readsGenericsWithNestedAnglesAndInlineObjectTypes() {
        FrontendSources fs = scan("g.ts", """
                class G {
                  a() {
                    return this.http.post<{ id: number; name: string }>('/api/a', {});
                  }
                  b() {
                    return this.http.get<Map<string, Array<Foo>>>('/api/b');
                  }
                }
                """, "");
        assertThat(fs.calls.get(0).generic()).isEqualTo("{ id: number; name: string }");
        assertThat(fs.calls.get(1).generic()).isEqualTo("Map<string, Array<Foo>>");
    }

    @Test
    void parsesInterfacesIncludingOptionalNestedAndInheritedFields() {
        FrontendSources fs = scan("t.ts", """
                export interface Base { id: number; }
                /** doc */
                export interface Child extends Base {
                  name: string;
                  note?: string;
                  nested: { a: number; b: string };
                  // comment: ignored
                  tags: string[];
                }
                export type Status = 'NEW' | 'DONE';
                """, "");
        FrontendSources.TsInterface child = fs.resolve("Child", "t.ts");
        assertThat(child.fields()).containsOnlyKeys("name", "note", "nested", "tags");
        assertThat(child.fields().get("note")).isTrue();
        assertThat(fs.allFields(child, new java.util.LinkedHashSet<>())).containsKeys("id", "name", "nested");
        assertThat(fs.stringUnions.get("Status")).containsExactly("NEW", "DONE");
    }

    @Test
    void ignoresReceiversThatAreNotHttpClients() {
        FrontendSources fs = scan("m.ts", "class M {\n  f() {\n    return this.cache.get('/api/x') + map.get('k');\n  }\n}\n", "");
        assertThat(fs.calls).isEmpty();
    }
}
